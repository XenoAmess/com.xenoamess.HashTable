import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HashTable<T> {

	class pair {
		protected T key;
		protected int val;

		// pair(){
		// val = 0;
		// }
		pair(T key) {
			this.key = key;
			val = 1;
		}
	}

	public ArrayList<LinkedList<pair>> pool;
	int nowPoolSize;
	int nodeSize;

	public HashTable() {
		super();
		init(50);
	}

	public HashTable(int initialPoolSize) {
		super();
		init(initialPoolSize);
	}

	private void init(int initialPoolSize) {
		this.nowPoolSize = initialPoolSize * 2 + 1;
		this.pool = new ArrayList(this.nowPoolSize);
		this.nodeSize = 0;
	}

	public int get(T t) {
		int hashCode = t.hashCode();
		int nowHashCode = hashCode % nowPoolSize;
		List<pair> list = pool.get(nowHashCode);
		if (list == null) {
			return 0;
		}
		for (pair au : list) {
			T nowt = au.key;
			if (nowt.equals(t)) {
				return au.val;
			}
		}

		return 0;
	}

	public void insert(T t) {
		int hashCode = t.hashCode();
		int nowHashCode = hashCode % nowPoolSize;
		List<pair> list = pool.get(nowHashCode);
		if (list == null) {
			list = new LinkedList<pair>();
			pool.set(nowHashCode, (LinkedList<pair>) list);
		}
		for (pair au : list) {
			T nowt = au.key;
			if (nowt.equals(t)) {
				au.val++;
			}
			return;
		}

		list.add(new pair(t));
	}

	public boolean delete(T t) {
		int hashCode = t.hashCode();
		int nowHashCode = hashCode % nowPoolSize;
		List<pair> list = pool.get(nowHashCode);
		if (list == null) {
			return false;
		}

		LinkedList<pair> list2 = new LinkedList<pair>();

		boolean res = false;

		for (pair au : list) {
			T nowt = au.key;
			if (nowt.equals(t)) {
				// list.erase(au);
				res = true;
			} else {
				list2.add(au);
			}
		}
		if (res) {
			list.clear();
			pool.set(nowHashCode, list2);
		}
		return res;
	}

}
