package com.xenoamess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.tree.TreeNode;

import com.xenoamess.HashTable.Table.SkipedListNode;

public class HashTable<K, V> implements Map<K, V> {
	static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash
	static final int MAX_POOL_SIZE = (1 << 16);
	static final int MIN_POOL_SIZE = (1 << 8);
	static final int TRANSFORM_LIMIT = 8;

	int getHashCode(Object k) {
		int hashCode = spread(k.hashCode());
		return hashCode & (nowPoolSize - 1);
	}

	/**
	 * Spreads (XORs) higher bits of hash to lower and also forces top bit to 0.
	 * Because the table uses power-of-two masking, sets of hashes that vary only in
	 * bits above the current mask will always collide. (Among known examples are
	 * sets of Float keys holding consecutive whole numbers in small tables.) So we
	 * apply a transform that spreads the impact of higher bits downward. There is a
	 * tradeoff between speed, utility, and quality of bit-spreading. Because many
	 * common sets of hashes are already reasonably distributed (so don't benefit
	 * from spreading), and because we use trees to handle large sets of collisions
	 * in bins, we just XOR some shifted bits in the cheapest possible way to reduce
	 * systematic lossage, as well as to incorporate impact of the highest bits that
	 * would otherwise never be used in index calculations because of table bounds.
	 */
	static final int spread(int h) {
		return (h ^ (h >>> 16)) & HASH_BITS;
	}

	static class HashTableEntry<K, V> implements Entry<K, V>, Comparable<HashTableEntry<K, V>> {
		protected final K key;
		protected volatile V value;

		HashTableEntry(K key, V value) {
			this.key = key;
			this.value = value;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}

		@Override
		public synchronized V setValue(V newValue) {
			V oldValue = this.value;
			this.value = newValue;
			return oldValue;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public int compareTo(HashTableEntry<K, V> a) {
			return ((Comparable) (this.getKey())).compareTo((Comparable) (a.getKey()));
		}

	}

	static class Node<K, V> {
		protected final Node<K, V> nextNode;
		protected final Entry<K, V> pair;

		Node(Node<K, V> nextNode, Entry<K, V> pair) {
			this.nextNode = nextNode;
			this.pair = pair;
		}
	}

	static class Table<K, V> {

		static class SkipedListNode<K, V> extends Node<K, V> {
			// protected final int distance;
			protected final SkipedListNode<K, V> downNode;

			SkipedListNode(SkipedListNode<K, V> nextNode, SkipedListNode<K, V> downNode, Entry<K, V> pair) {
				super(nextNode, pair);
				this.downNode = downNode;
				// this.distance = distance;
			}
		}

		@SuppressWarnings("hiding")
		class SkipedListMap<K, V> implements Map<K, V> {

			AtomicInteger layer = new AtomicInteger(0);

			SkipedListNode<K, V> heads[];

			protected SkipedListNode<K, V>[] getHeads() {
				while (true) {
					if (condition.get() == 0) {
						return heads;
					}
				}
			}

			SkipedListMap() {
				init();
			}

			@SuppressWarnings("unchecked")
			void init() {
				while ((1 << layer.get()) <= tableNodeSize.get()) {
					layer.getAndIncrement();
				}

				heads = new SkipedListNode[layer.get()];

				ArrayList<Entry<K, V>> pairs = new ArrayList<Entry<K, V>>();
				Node<K, V> nowNode = (Node<K, V>) head;
				while (nowNode != null) {
					pairs.add(nowNode.pair);
					nowNode = nowNode.nextNode;
				}
				pairs.sort(null);

				SkipedListNode<K, V>[] lastHeads = new SkipedListNode[pairs.size()];

				for (int i = 0; i < layer.get(); i++) {
					for (int j = (pairs.size() - 1) >> i; j >= 0; j--) {
						heads[i] = new SkipedListNode<K, V>(heads[i], lastHeads[j << i], pairs.get(j << i));
						lastHeads[j << i] = heads[i];
					}
				}

				pairs.clear();

			}

			@Override
			public void clear() {
				layer.set(0);
				heads = null;
			}

			@SuppressWarnings("unchecked")
			@Override
			public V get(Object o) {
				K k = (K) o;
				SkipedListNode<K, V>[] nowHeads = getHeads();
				SkipedListNode<K, V> nowNode = nowHeads[layer.get() - 1];
				SkipedListNode<K, V> nextNode = null;
				int cmpNow = ((Comparable<K>) nowNode.pair.getKey()).compareTo(k);
				if (cmpNow == 0) {
					return nowNode.pair.getValue();
				}

				while (nowNode != null) {
					nextNode = (SkipedListNode<K, V>) nowNode.nextNode;

					int cmpNext;
					if (nextNode == null) {
						cmpNext = 1;
					} else {
						cmpNext = ((Comparable<K>) nextNode.pair.getKey()).compareTo(k);
					}

					if (cmpNext == 0) {
						return nextNode.pair.getValue();
					} else if (cmpNext < 0) {
						nowNode = (SkipedListNode<K, V>) nowNode.nextNode;
					} else {
						nowNode = nowNode.downNode;
					}
				}
				return null;
			}

			@SuppressWarnings("unchecked")
			@Override
			public V put(K k, V v) {
				V res = null;

				SkipedListNode<K, V> nowNode = heads[layer.get() - 1];
				SkipedListNode<K, V> nextNode = null;

				int newLayer = layer.get();
				int nowLayer = newLayer - 1;

				while ((1 << newLayer) <= tableNodeSize.get() + 1) {
					++newLayer;
				}
				SkipedListNode<K, V>[] newHeads = new SkipedListNode[newLayer];
				// SkipedListNode<K, V>[] oldBack = new SkipedListNode[newLayer];

				int cmpNow = ((Comparable<K>) nowNode.pair.getKey()).compareTo(k);
				if (cmpNow == 0) {
					res = nowNode.pair.getValue();
					nowNode.pair.setValue(v);
					return res;
				}

				while (nowNode != null) {
					nextNode = (SkipedListNode<K, V>) nowNode.nextNode;

					int cmpNext;
					if (nextNode == null) {
						cmpNext = 1;
					} else {
						cmpNext = ((Comparable<K>) nextNode.pair.getKey()).compareTo(k);
					}

					if (cmpNext == 0) {
						res = nextNode.pair.getValue();
						nextNode.pair.setValue(v);
						return res;
					} else if (cmpNext < 0) {
						nowNode = (SkipedListNode<K, V>) nowNode.nextNode;
					} else {
						newHeads[nowLayer] = (SkipedListNode<K, V>) nowNode.nextNode;
						if (nowNode.downNode == null) {
							ArrayList<Entry<K, V>> pairs = new ArrayList<Entry<K, V>>();
							for (@SuppressWarnings("rawtypes")
							Node ni = heads[0]; ni != nowNode; ni = ni.nextNode) {
								if (ni.pair.getValue() != null) {
									pairs.add(ni.pair);
								} else {
									tableNodeSize.getAndDecrement();
								}
							}

							if (nowNode.pair.getValue() != null) {
								pairs.add(nowNode.pair);
							} else {
								tableNodeSize.getAndDecrement();
							}

							if (v != null) {
								pairs.add(new HashTableEntry<K, V>(k, v));
							} else {
								tableNodeSize.getAndDecrement();
							}

							// System.out.println(nowLayer);
							// System.out.println(pairs.size());
							// for (Entry<K, V> pair : pairs) {
							// System.out.println(pair.getKey() + " " + pair.getValue());
							// }

							SkipedListNode<K, V>[] lastHeads = new SkipedListNode[pairs.size()];

							for (int i = 0; i < newLayer; i++) {
								for (int j = (pairs.size() - 1) >> i; j >= 0; j--) {
									newHeads[i] = new SkipedListNode<K, V>(newHeads[i], lastHeads[j << i],
											pairs.get(j << i));
									lastHeads[j << i] = newHeads[i];
								}
							}
							pairs.clear();
							heads = newHeads;
							layer.set(newLayer);
							tableNodeSize.getAndIncrement();
							//
							// for (int i = newLayer - 1; i >= 0; i--) {
							// SkipedListNode nn = heads[i];
							// while (nn != null) {
							// System.out.print(nn.pair.getKey() + "," + nn.pair.getValue() + " ");
							// nn = (SkipedListNode) nn.nextNode;
							// }
							// System.out.println();
							// }
							// if (true)
							// throw new RuntimeException("e");
							return null;
						}
						nowNode = nowNode.downNode;
						nowLayer--;
					}
				}

				res = null;
				return res;
			}

			@SuppressWarnings({ "unchecked" })
			@Override
			public V remove(Object o) {
				return put((K) o, null);
			}

			@Override
			public boolean containsKey(Object arg0) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public boolean containsValue(Object arg0) {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public Set<Entry<K, V>> entrySet() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public boolean isEmpty() {
				// TODO Auto-generated method stub
				return false;
			}

			@Override
			public Set<K> keySet() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void putAll(Map<? extends K, ? extends V> arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public int size() {
				// TODO Auto-generated method stub
				return 0;
			}

			@Override
			public Collection<V> values() {
				// TODO Auto-generated method stub
				return null;
			}

		}

		protected volatile AtomicInteger condition = new AtomicInteger();
		protected Node<K, V> head = null;
		protected AtomicInteger tableNodeSize = new AtomicInteger();
		protected AtomicBoolean transformed = new AtomicBoolean();
		protected SkipedListMap<K, V> skipedListMap = null;

		protected Node<K, V> getHead() {
			while (true) {
				if (condition.get() == 0) {
					return head;
				}
			}
		}

		protected synchronized void workBegin() {
			while (true) {
				if (condition.compareAndSet(0, 1)) {
					return;
				}
			}
		}

		protected synchronized void workEnd() {
			condition.set(0);
		}

		protected V get(K k) {
			if (!transformed.get()) {
				Node<K, V> nowNode = this.getHead();
				while (nowNode != null) {
					if (nowNode.pair.getKey().equals(k)) {
						return nowNode.pair.getValue();
					}
					nowNode = nowNode.nextNode;
				}
				return null;
			} else {
				V res = skipedListMap.get(k);
				return res;
			}
		}

		protected synchronized V put(K k, V v) {
			V res = null;
			this.workBegin();
			if (!transformed.get()) {

				Node<K, V> nowNode = this.head;

				while (nowNode != null) {
					if (nowNode.pair.getKey().equals(k)) {
						res = nowNode.pair.getValue();
						nowNode.pair.setValue(v);
						this.workEnd();
						return res;
					}
					nowNode = nowNode.nextNode;
				}

				this.head = new Node<K, V>(this.head, new HashTableEntry<K, V>(k, v));

				tableNodeSize.getAndIncrement();

				if (!transformed.get() && Comparable.class.isAssignableFrom(k.getClass())
						&& tableNodeSize.get() >= TRANSFORM_LIMIT) {
					this.transform();
				}
			} else {
				res = skipedListMap.put(k, v);
			}
			this.workEnd();
			return res;

		}

		protected synchronized V remove(K k) {
			V res = null;
			this.workBegin();
			if (!transformed.get()) {
				Node<K, V> nowNode = this.head;
				Node<K, V> oldNode = nowNode;

				if (nowNode == null) {
					this.workEnd();
					return null;
				}

				while (nowNode != null) {
					if (nowNode.pair.getKey().equals(k)) {
						res = nowNode.pair.getValue();
						Node<K, V> newNode = nowNode.nextNode;
						while (oldNode != nowNode) {
							newNode = new Node<K, V>(newNode, oldNode.pair);
							oldNode = oldNode.nextNode;
						}
						this.head = newNode;
						tableNodeSize.getAndDecrement();
						this.workEnd();
						return res;
					}
					nowNode = nowNode.nextNode;
				}
			} else {
				res = skipedListMap.remove(k);
			}
			this.workEnd();
			return res;
		}

		protected synchronized void resizeSplit(int nowHashcode, Table<K, V>[] newPool, int newPoolSize) {
			Table<K, V> bigger = new Table<K, V>();
			Table<K, V> smaller = new Table<K, V>();
			this.workBegin();

			Node<K, V> nowNode;
			if (!transformed.get()) {
				nowNode = this.head;
			} else {
				nowNode = this.skipedListMap.heads[0];
			}

			while (nowNode != null) {
				int hashCode = spread(nowNode.pair.getKey().hashCode());
				hashCode &= (newPoolSize - 1);

				if (hashCode == nowHashcode) {
					smaller.head = new Node<K, V>(smaller.head, nowNode.pair);
					smaller.tableNodeSize.getAndIncrement();
				} else {
					bigger.head = new Node<K, V>(bigger.head, nowNode.pair);
					bigger.tableNodeSize.getAndIncrement();
				}
				nowNode = nowNode.nextNode;
			}
			newPool[nowHashcode] = smaller;
			newPool[nowHashcode + (newPoolSize >> 1)] = bigger;

			this.workEnd();
			return;
		}

		protected synchronized void transform() {
			if (transformed.get())
				return;
			skipedListMap = new SkipedListMap<K, V>();
			transformed.set(true);
			this.head = null;
		}

	}

	public Table<K, V>[] pool;

	volatile int nowPoolSize = 0;
	AtomicInteger nodeSize = new AtomicInteger();
	AtomicInteger condition = new AtomicInteger();

	public int getNowPoolSize() {
		return nowPoolSize;
	}

	public HashTable() {
		super();
		init(MIN_POOL_SIZE);
	}

	public HashTable(int initPoolSize) {
		super();
		init(initPoolSize);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void init(int initPoolSize) {
		if (initPoolSize < MIN_POOL_SIZE) {
			initPoolSize = MIN_POOL_SIZE;
		} else if (initPoolSize > MAX_POOL_SIZE) {
			initPoolSize = MAX_POOL_SIZE;
		}

		++initPoolSize;
		this.nowPoolSize = 1;
		while (this.nowPoolSize < initPoolSize) {
			this.nowPoolSize = this.nowPoolSize << 1;
		}
		this.pool = new HashTable.Table[this.nowPoolSize];
		this.nodeSize.set(0);
		for (int i = 0; i < this.nowPoolSize; i++) {
			pool[i] = new HashTable.Table();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public V get(Object o) {
		if (o == null) {
			return null;
		}
		K k = null;
		try {
			k = (K) (o);
		} catch (java.lang.ClassCastException e) {
			return null;
		}

		int nowHashCode = getHashCode(k);

		// System.out.println(nowPoolSize + " " + nowHashCode + " " +
		// pool[nowHashCode]);

		Table<K, V> nowTable = pool[nowHashCode];
		return nowTable.get(k);
	}

	public synchronized void resize() {
		if (condition.get() == 1) {
			return;
		}

		if (nowPoolSize >= MAX_POOL_SIZE) {
			return;
		}

		if (!(nodeSize.get() >= nowPoolSize - (nowPoolSize >> 2))) {
			return;
		}

		condition.set(1);
		int newPoolSize = (nowPoolSize << 1);
		Table<K, V>[] oldPool = pool;
		@SuppressWarnings("unchecked")
		Table<K, V>[] newPool = new HashTable.Table[newPoolSize];

		for (int i = 0; i < nowPoolSize; i++) {
			oldPool[i].resizeSplit(i, newPool, newPoolSize);
		}
		pool = newPool;
		nowPoolSize = newPoolSize;
		condition.set(0);
	}

	@Override
	public synchronized void clear() {
		init(MIN_POOL_SIZE);
	}

	@Override
	public boolean containsKey(Object o) {
		if (this.get(o) == null) {
			return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean containsValue(Object o) {
		if (o == null) {
			return false;
		}
		V v = null;
		try {
			v = (V) (o);
		} catch (java.lang.ClassCastException e) {
			return false;
		}

		Table<K, V>[] oldPool = pool;
		for (int i = 0; i < nowPoolSize; i++) {
			Node<K, V> nowNode = oldPool[i].getHead();

			while (nowNode != null) {
				if (nowNode.pair.getValue().equals(v))
					return true;
				nowNode = nowNode.nextNode;
			}
		}
		return false;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		HashSet<Entry<K, V>> entrySet = new HashSet<Entry<K, V>>();

		Table<K, V>[] oldPool = pool;
		for (int i = 0; i < nowPoolSize; i++) {
			Node<K, V> nowNode = oldPool[i].getHead();

			while (nowNode != null) {
				entrySet.add(nowNode.pair);
				nowNode = nowNode.nextNode;
			}
		}

		return entrySet;
	}

	@Override
	public boolean isEmpty() {
		return (nodeSize.get() > 0);
	}

	@Override
	public Set<K> keySet() {
		HashSet<K> keySet = new HashSet<K>();

		Table<K, V>[] oldPool = pool;
		for (int i = 0; i < nowPoolSize; i++) {
			Node<K, V> nowNode = oldPool[i].getHead();

			while (nowNode != null) {
				keySet.add(nowNode.pair.getKey());
				nowNode = nowNode.nextNode;
			}
		}

		return keySet;
	}

	@Override
	public V put(K k, V v) {
		while (condition.get() != 0) {
		}

		int nowHashCode = getHashCode(k);

		// System.out.println(nowPoolSize + " " + nowHashCode + " " +
		// pool[nowHashCode]);

		Table<K, V> nowTable = pool[nowHashCode];
		V res = nowTable.put(k, v);

		if (res != null)
			nodeSize.getAndIncrement();
		if (nodeSize.get() >= nowPoolSize - (nowPoolSize >> 2)) {
			resize();
		}
		return res;
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		for (Entry<? extends K, ? extends V> entry : map.entrySet()) {
			this.put(entry.getKey(), entry.getValue());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object o) {
		if (o == null) {
			return null;
		}
		K k = null;
		try {
			k = (K) (o);
		} catch (java.lang.ClassCastException e) {
			return null;
		}

		if (!containsKey(o)) {
			return null;
		}

		while (condition.get() != 0) {
		}

		int nowHashCode = getHashCode(k);
		Table<K, V> nowTable = pool[nowHashCode];
		V res = nowTable.remove(k);
		if (res != null) {
			nodeSize.getAndDecrement();
		}
		return res;
	}

	@Override
	public int size() {
		return nodeSize.get();
	}

	@Override
	public Collection<V> values() {
		ArrayList<V> values = new ArrayList<V>();

		Table<K, V>[] oldPool = pool;
		for (int i = 0; i < nowPoolSize; i++) {
			Node<K, V> nowNode = oldPool[i].getHead();

			while (nowNode != null) {
				values.add(nowNode.pair.getValue());
				nowNode = nowNode.nextNode;
			}
		}

		return values;
	}

}
