package com.xenoamess;

import javax.xml.validation.Validator;

public class HashTable<K, V> {
	static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash

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

	class Pair {
		protected final K key;
		protected volatile V value;

		Pair(K key, V value) {
			this.key = key;
			this.value = value;
		}
	}

	class Node {
		protected final Node nextNode;
		protected final Pair pair;

		Node(Node nextNode, Pair pair) {
			this.nextNode = nextNode;
			this.pair = pair;
		}
	}

	class Table {
		protected volatile int condition = 0;
		protected Node head = null;

		protected Node getHead() {
			while (true) {
				if (condition != 0) {
					System.out.println("getHead");
					System.out.println(head);
					System.out.println(condition);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
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
					System.out.println("workBegin");
					System.out.println(head);
					System.out.println(condition);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
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

	}

	public Table[] pool;

	int nowPoolSize;
	int nodeSize;

	public HashTable() {
		super();
		init(128);
	}

	public HashTable(int nowPoolSize) {
		super();
		init(nowPoolSize);
	}

	@SuppressWarnings("unchecked")
	private void init(int nowPoolSize) {
		this.nowPoolSize = nowPoolSize;
		this.pool = new HashTable.Table[this.nowPoolSize];
		this.nodeSize = 0;
		for (int i = 0; i < this.nowPoolSize; i++) {
			pool[i] = new HashTable.Table();
		}
	}

	public V get(K k) {
		int hashCode = spread(k.hashCode());
		int nowHashCode = hashCode % nowPoolSize;
		Table nowTable = pool[nowHashCode];
		Node nowNode = nowTable.getHead();

		while (nowNode != null) {
			if (nowNode.pair.key.equals(k)) {
				return nowNode.pair.value;
			}
			nowNode = nowNode.nextNode;
		}

		return null;
	}

	public void insert(K k, V v) {

		int hashCode = spread(k.hashCode());
		int nowHashCode = hashCode % nowPoolSize;

		Table nowTable = pool[nowHashCode];

		synchronized (nowTable) {
			nowTable.workBegin();
			if (nowTable.head == null) {
				nowTable.head = new Node(null, new Pair(k, v));
				nowTable.workEnd();
				return;
			}
			Node nowNode = nowTable.head;

			while (nowNode != null) {
				if (nowNode.pair.key.equals(k)) {
					nowNode.pair.value = v;
					nowTable.workEnd();
					return;
				}
				nowNode = nowNode.nextNode;
			}
			nowTable.head = new Node(nowTable.head, new Pair(k, v));
			nowTable.workEnd();
			return;
		}
	}

	public boolean delete(K k) {
		int hashCode = spread(k.hashCode());
		int nowHashCode = hashCode % nowPoolSize;

		Table nowTable = pool[nowHashCode];

		synchronized (nowTable) {
			nowTable.workBegin();

			Node nowNode = nowTable.head;
			Node oldNode = nowNode;

			if (nowNode == null) {
				nowTable.workEnd();
				return false;
			}

			while (nowNode != null) {
				if (nowNode.pair.key.equals(k)) {
					Node newNode = nowNode;
					while (oldNode != nowNode) {
						newNode = new Node(newNode, oldNode.pair);
						oldNode = oldNode.nextNode;
					}
					pool[nowHashCode].head = newNode;
					nowTable.workEnd();
					return true;
				}
				nowNode = nowNode.nextNode;
			}
			nowTable.workEnd();
			return false;
		}
	}
}