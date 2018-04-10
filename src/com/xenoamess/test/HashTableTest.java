package com.xenoamess.test;

import com.xenoamess.HashTable;

public class HashTableTest {
	public static final int TEST_TURNS = (1 << 12);
	public static final int TEST_THREADS = 16;
	public static final int TEST_MAX = 100;

	public static HashTable<Integer, Integer> testedHashTable;

	public static Integer rand() {
		return (int) (Math.random() * TEST_MAX);
	}

	static class TestThread implements Runnable {
		Integer i = 20;

		boolean bo = i.equals("20");

		int index;

		TestThread(int index) {
			super();
			this.index = index;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			for (int i = 0; i < TEST_TURNS; i++) {
				int key;
				int value;

				key = rand();
				value = rand();
				System.out.printf("thread %d , turns %d , before insert , arguments:%d %d\n", this.index, i, key,
						value);
				testedHashTable.insert(key, value);
				System.out.printf("thread %d , turns %d , after insert , arguments:%d %d\n", this.index, i, key, value);

				key = rand();
				System.out.printf("thread %d , turns %d , before get , arguments:%d\n", this.index, i, key);
				Integer res2 = testedHashTable.get(key);
				System.out.printf("thread %d , turns %d , after get , arguments:%d , res=%d\n", this.index, i, key,
						res2 == null ? -1 : res2.intValue());

				key = rand();
				System.out.printf("thread %d , turns %d , before delete , arguments:%d\n", this.index, i, key);
				boolean res3 = testedHashTable.delete(key);
				System.out.printf("thread %d , turns %d , after delete , arguments:%d , res=%d\n", this.index, i, key,
						res3 ? 1 : 0);

			}
		}
	}

	public static void main(String args[]) {
		testedHashTable = new HashTable<Integer, Integer>(1 << 10);
		for (int i = 0; i < TEST_THREADS; i++) {
			new Thread(new TestThread(i)).start();
		}
	}
}
