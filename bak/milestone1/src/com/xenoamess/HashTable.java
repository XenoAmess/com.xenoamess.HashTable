package com.xenoamess;

public class HashTable<K, V> {
	static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash
	static final int MAX_POOL_SIZE = (1 << 16);
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
		protected int tableNodeSize = 0;
		protected boolean transformed = false;

		
		protected Node getHead() {
			while (true) {
				if (condition != 0) {
					// System.out.println("getHead");
					// System.out.println(head);
					// System.out.println(condition);
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
					// System.out.println("workBegin");
					// System.out.println(head);
					// System.out.println(condition);
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

		protected V get(K k) {
			Node nowNode = this.getHead();
			while (nowNode != null) {
				if (nowNode.pair.key.equals(k)) {
					// System.out.println("get " + nowNode.pair.key + " " + k + " " +
					// nowNode.pair.value);
					return nowNode.pair.value;
				}
				nowNode = nowNode.nextNode;
			}
			return null;
		}

		protected synchronized void insert(K k, V v) {
			this.workBegin();
			Node nowNode = this.head;

			while (nowNode != null) {
				if (nowNode.pair.key.equals(k)) {
					nowNode.pair.value = v;
					this.workEnd();
					return;
				}
				nowNode = nowNode.nextNode;
			}

			this.head = new Node(this.head, new Pair(k, v));
			++nodeSize;
			++tableNodeSize;

			if (tableNodeSize > TRANSFORM_LIMIT) {
				this.transform();
			}
			this.workEnd();
			return;
		}

		protected synchronized V delete(K k) {
			this.workBegin();

			Node nowNode = this.head;
			Node oldNode = nowNode;

			if (nowNode == null) {
				this.workEnd();
				return null;
			}

			while (nowNode != null) {
				if (nowNode.pair.key.equals(k)) {
					V lastValue = nowNode.pair.value;
					Node newNode = nowNode.nextNode;
					while (oldNode != nowNode) {
						newNode = new Node(newNode, oldNode.pair);
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
			Node nowNode = this.head;
			while (nowNode != null) {
				if (getNewHashCode(nowNode.pair.key) == nowHashcode) {
					smaller.head = new Node(smaller.head, nowNode.pair);
					++smaller.tableNodeSize;
				} else {
					bigger.head = new Node(bigger.head, nowNode.pair);
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
		init(128);
	}

	public HashTable(int initPoolSize) {
		super();
		init(initPoolSize);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void init(int initPoolSize) {
		if (initPoolSize < 1) {
			initPoolSize = 1;
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

	public V get(K k) {
		int nowHashCode = getHashCode(k);

		Table nowTable = pool[nowHashCode];

		return nowTable.get(k);
	}

	public void insert(K k, V v) {
		while (condition != 0) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		int nowHashCode = getHashCode(k);
		Table nowTable = pool[nowHashCode];
		nowTable.insert(k, v);
		if (nodeSize >= nowPoolSize - (nowPoolSize >> 2)) {
			resize();
		}
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
		Table[] newPool = new HashTable.Table[newPoolSize];
		for (int i = 0; i < nowPoolSize; i++) {
			pool[i].resizeSplit(i, newPool, newPoolSize);
		}
		pool = newPool;
		nowPoolSize = newPoolSize;
		condition = 0;

	}

}
