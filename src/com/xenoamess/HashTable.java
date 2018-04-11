package com.xenoamess;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public class HashTable<K, V> implements Map<K, V> {
	static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash
	static final int MAX_POOL_SIZE = (1 << 16);
	static final int MIN_POOL_SIZE = (1 << 8);
	static final int TRANSFORM_LIMIT = 8;

	int getHashCode(K k) {
		int hashCode = spread(k.hashCode());
		return hashCode & (nowPoolSize - 1);
	}

	int getNewHashCode(K k) {
		int hashCode = spread(k.hashCode());
		return hashCode & ((nowPoolSize << 1) - 1);
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

	// class Pair {
	// protected final K key;
	// protected volatile V value;
	//
	// Pair(K key, V value) {
	// this.key = key;
	// this.value = value;
	// }
	// }

	static class HashTableEntry<K, V> implements Entry<K, V> {
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
		public V setValue(V newValue) {
			V oldValue = this.value;
			this.value = newValue;
			return oldValue;
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

	class Table {

		protected volatile int condition = 0;
		protected Node<K, V> head = null;
		protected int tableNodeSize = 0;
		protected boolean transformed = false;

		protected Node<K, V> getHead() {
			while (true) {
				if (condition != 0) {
					// System.out.println("getHead");
					// System.out.println(head);
					// System.out.println(condition);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					return head;
				}
			}
		}

		protected synchronized void workBegin() {
			while (true) {
				if (condition != 0) {
					// System.out.println("workBegin");
					// System.out.println(head);
					// System.out.println(condition);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} else {
					++condition;
					return;
				}
			}
		}

		protected synchronized void workEnd() {
			--condition;
		}

		protected V get(K k) {
			Node<K, V> nowNode = this.getHead();
			while (nowNode != null) {
				if (nowNode.pair.getKey().equals(k)) {
					// System.out.println("get " + nowNode.pair.key + " " + k + " " +
					// nowNode.pair.value);
					return nowNode.pair.getValue();
				}
				nowNode = nowNode.nextNode;
			}
			return null;
		}

		protected synchronized V insert(K k, V v) {
			V res = null;
			this.workBegin();
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
			++nodeSize;
			++tableNodeSize;

			if (tableNodeSize > TRANSFORM_LIMIT) {
				this.transform();
			}
			this.workEnd();
			return res;
		}

		protected synchronized V delete(K k) {
			this.workBegin();

			Node<K, V> nowNode = this.head;
			Node<K, V> oldNode = nowNode;

			if (nowNode == null) {
				this.workEnd();
				return null;
			}

			while (nowNode != null) {
				if (nowNode.pair.getKey().equals(k)) {
					V lastValue = nowNode.pair.getValue();
					Node<K, V> newNode = nowNode.nextNode;
					while (oldNode != nowNode) {
						newNode = new Node<K, V>(newNode, oldNode.pair);
						oldNode = oldNode.nextNode;
					}
					this.head = newNode;
					--nodeSize;
					--tableNodeSize;
					this.workEnd();
					return lastValue;
				}
				nowNode = nowNode.nextNode;
			}
			this.workEnd();
			return null;

		}

		protected synchronized void resizeSplit(int nowHashcode, HashTable<K, V>.Table[] newPool, int newPoolSize) {
			Table bigger = new Table();
			Table smaller = new Table();
			this.workBegin();
			Node<K, V> nowNode = this.head;
			while (nowNode != null) {
				if (getNewHashCode(nowNode.pair.getKey()) == nowHashcode) {
					smaller.head = new Node<K, V>(smaller.head, nowNode.pair);
					++smaller.tableNodeSize;
				} else {
					bigger.head = new Node<K, V>(bigger.head, nowNode.pair);
					++bigger.tableNodeSize;
				}
				nowNode = nowNode.nextNode;
			}
			newPool[nowHashcode] = smaller;
			newPool[nowHashcode + (newPoolSize >> 1)] = bigger;
			this.workEnd();
			return;
		}

		protected synchronized void transform() {

		}

	}

	public Table[] pool;

	int nowPoolSize;
	int nodeSize;
	volatile int condition = 0;

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
		this.nodeSize = 0;
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

		Table nowTable = pool[nowHashCode];

		return nowTable.get(k);
	}

	public V delete(K k) {
		while (condition != 0) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		int nowHashCode = getHashCode(k);
		Table nowTable = pool[nowHashCode];
		return nowTable.delete(k);
	}

	public synchronized void resize() {
		if (nowPoolSize >= MAX_POOL_SIZE) {
			return;
		}
		if (condition == 1) {
			return;
		}

		condition = 1;
		int newPoolSize = (nowPoolSize << 1);
		Table[] oldPool = pool;
		@SuppressWarnings("unchecked")
		Table[] newPool = new HashTable.Table[newPoolSize];

		for (int i = 0; i < nowPoolSize; i++) {
			oldPool[i].resizeSplit(i, newPool, newPoolSize);
		}
		pool = newPool;
		nowPoolSize = newPoolSize;
		condition = 0;

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

	@Override
	public boolean containsValue(Object arg0) {
		// TODO Auto-generated method stub
		throw new RuntimeException("workNotFinish exception");
		// return false;
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		Set<Entry<K, V>> entrySet = new HashSet<Entry<K, V>>();

		Table[] oldPool = pool;
		for (int i = 0; i < nowPoolSize; i++) {
			oldPool[i].getHead();

		}

		return entrySet;
	}

	@Override
	public boolean isEmpty() {
		return (nodeSize > 0);
	}

	@Override
	public Set<K> keySet() {
		// TODO Auto-generated method stub
		throw new RuntimeException("workNotFinish exception");
		// return null;
	}

	@Override
	public V put(K k, V v) {
		while (condition != 0) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		int nowHashCode = getHashCode(k);
		Table nowTable = pool[nowHashCode];
		V res = nowTable.insert(k, v);
		if (nodeSize >= nowPoolSize - (nowPoolSize >> 2)) {
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
		return delete(k);
	}

	@Override
	public int size() {
		return nodeSize;
	}

	@Override
	public Collection<V> values() {
		// TODO Auto-generated method stub
		throw new RuntimeException("workNotFinish exception");
		// return null;
	}

}
