package com.wggt.compare;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import com.wggt.compare.dao.impl.ScanDaoImpl;
import com.wggt.compare.pojo.Path;
import com.wggt.compare.thread.ScanCompareRunable;


@SpringBootTest
class CompareApplicationTests {

	@Test
	public void testSearchFile() throws Exception {
		ScanDaoImpl scanDaoImpl = new ScanDaoImpl();
		scanDaoImpl.list("D:\\Java_Learn\\Compare\\Data\\扫短视线内背景对比源");
	}

	@Test
	public void testCompare() throws IOException {
		ExecutorService pool = new ThreadPoolExecutor(3, 5, 6, TimeUnit.SECONDS, 
		new ArrayBlockingQueue<>(5), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
		Path path = new Path("D:/Java_Learn/Compare/Data/扫短视线内背景对比源", "D:/Java_Learn/Compare/Data/扫描短波_04月03日10时01分49秒_解码数据"); 
		Runnable target = new ScanCompareRunable(path);
        // Runnable target = new MyRunnable();
        pool.execute(target);
		// 原因是因为做单元测试时跟WEB项目不同，线程还没有开始启动，主线程已经关闭，要加入一段代码，让主线程不关闭，这样就可以跑子线程的方法了
		System.in.read();
	}

}
