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

	// class Table {
	//
	// }

	public Node[] pool;

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
		this.pool = (HashTable<K, V>.Node[]) new Object[this.nowPoolSize];
		this.nodeSize = 0;
	}

	public V get(K k) {
		int hashCode = spread(k.hashCode());
		int nowHashCode = hashCode % nowPoolSize;
		Node nowNode = pool[nowHashCode];
		if (nowNode == null) {
			return null;
		}

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

		Node nowNode = pool[nowHashCode];
		if (nowNode == null) {
			nowNode = new Node(null, new Pair(k, v));
			pool[nowHashCode] = nowNode;
			return;
		}

		while (nowNode != null) {
			if (nowNode.pair.key.equals(k)) {
				nowNode.pair.value = v;
				return;
			}
			nowNode = nowNode.nextNode;
		}
		pool[nowHashCode] = new Node(pool[nowHashCode], new Pair(k, v));
	}

	public boolean delete(K k) {
		int hashCode = spread(k.hashCode());
		int nowHashCode = hashCode % nowPoolSize;

		Node nowNode = pool[nowHashCode];
		Node oldNode = nowNode;

		if (nowNode == null) {
			return false;
		}

		while (nowNode != null) {
			if (nowNode.pair.key.equals(k)) {
				Node newNode = nowNode;
				while (oldNode != nowNode) {
					newNode = new Node(newNode, oldNode.pair);
					oldNode = oldNode.nextNode;
				}
				pool[nowHashCode] = newNode;
				return true;
			}
			nowNode = nowNode.nextNode;
		}
		return false;
	}

}
