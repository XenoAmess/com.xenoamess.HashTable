package com.xenoamess.test;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.xenoamess.HashTable;

public class HashTableTest {
	public static int TEST_TURNS = (1 << 14);
	public static int TEST_THREADS = 12;
	public static int TEST_MAX = TEST_TURNS;
	public static final String tester = "XenoAmess";
	public static HashTable<Integer, Integer> testedHashTable;

	public static boolean LOG = true;

	public static Integer rand() {
		return (int) (Math.random() * TEST_MAX);
	}

	static PrintStream printStream = null;

	static void init(String fileName) {
		if (printStream != null) {
			printStream.flush();
			printStream.close();
			printStream = null;
		}
		if (fileName == null) {
			printStream = System.out;
			return;
		}
		try {
			printStream = new PrintStream(new BufferedOutputStream(new FileOutputStream(fileName)));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static void println(Object object) {
		if (LOG)
			printStream.println(object);
	}

	static void printf(String format, Object... args) {
		if (LOG)
			printStream.printf(format, args);
	}

	static volatile int CNT;

	static class Move {
		int method;
		Integer key;
		Integer value;
		Integer res;

		public Move(int method, Integer key, Integer value, Integer res) {
			this.method = method;
			this.key = key;
			this.value = value;
			this.res = res;
		}
	}

	static ConcurrentLinkedDeque<Move> arr;

	static class TestThread implements Runnable {
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
				printf("thread %d , turns %d , before insert , arguments:%d %d\n", this.index, i, key, value);
				arr.add(new Move(0, key, value, null));
				testedHashTable.put(key, value);
				// printf("thread %d , turns %d , after insert , arguments:%d %d\n",
				// this.index, i, key, value);

				key = rand();
				// printf("thread %d , turns %d , before get , arguments:%d\n",
				// this.index, i, key);
				Integer res2 = testedHashTable.get(key);
				printf("thread %d , turns %d , after get , arguments:%d , res=%d\n", this.index, i, key,
						res2 == null ? -1 : res2.intValue());
				arr.add(new Move(1, key, value, res2));

				key = rand();
				// printf("thread %d , turns %d , before delete , arguments:%d\n",
				// this.index, i, key);
				Integer res3 = testedHashTable.remove(key);
				printf("thread %d , turns %d , after delete , arguments:%d , res=%d\n", this.index, i, key,
						res3 == null ? -1 : res3.intValue());
				arr.add(new Move(2, key, value, res3));
			}

			CNT--;
		}
	}

	static void printHead() {
		println(new Date());

		String className = new Object() {
			public String getClassName() {
				return this.getClass().getName().substring(0, this.getClass().getName().lastIndexOf('$'));
			}
		}.getClassName();
		println("TestName: " + className);
		println("Tester: " + tester);
		println("");
	}

	static void multipleThreadTest() {
		arr = new ConcurrentLinkedDeque<Move>();
		CNT = TEST_THREADS;
		testedHashTable = new HashTable<Integer, Integer>(64);
		for (int i = 0; i < TEST_THREADS; i++) {
			new Thread(new TestThread(i)).start();
		}
		while (CNT != 0) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		println("multipleThreadTest ends");
	}

	static void singleThreadTest() {
		arr = new ConcurrentLinkedDeque<Move>();
		CNT = 1;
		testedHashTable = new HashTable<Integer, Integer>(64);
		new Thread(new TestThread(0)).start();

		while (CNT != 0) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		println("singleThreadTest ends");
	}

	static void findSuspiciousError() {
		println("Size : " + testedHashTable.size());
		println("nowPoolSize : " + testedHashTable.getNowPoolSize());
		ConcurrentHashMap<Integer, Integer> judger = new ConcurrentHashMap<Integer, Integer>();
		int i = 4;
		boolean findBug = false;
		for (Move move : arr) {
			i++;
			if (move.method == 0) {
				judger.put(move.key, move.value);
			} else if (move.method == 1) {
				Integer res2 = judger.get(move.key);
				if (((res2 == null) != (move.res == null)) || (res2 != null && !res2.equals(move.res))) {
					printf("at Line %d : suspicious error found : 'get' get wrong answer?\n", i);
					findBug = true;
				}
			} else if (move.method == 2) {
				Integer res2 = judger.remove(move.key);
				if (((res2 == null) != (move.res == null)) || (res2 != null && !res2.equals(move.res))) {
					printf("at Line %d : suspicious error found : 'delete' get wrong answer?\n", i);
					findBug = true;
				}
			}
		}
		if (!findBug) {
			println("no suspicious errors found.");
		}
		println("findSuspiciousError ends");
	}

	public static void onExit() {
		if (printStream != null) {
			printStream.flush();
			printStream.close();
			printStream = null;
		}
	}

	public static void main(String args[]) {
		// LOG = false;

		init("singleThreadTest.txt");
		// init(null);
		printHead();
		singleThreadTest();
		findSuspiciousError();

		init("multipleThreadTest.txt");
		// init(null);
		printHead();
		multipleThreadTest();
		findSuspiciousError();

		TEST_TURNS = 1 << 20;
		LOG = false;
		multipleThreadTest();
		onExit();
	}
}
