package com.aichat.study.concurrency.zj_01;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class zj01 {
    
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);
    public static void main(String[] args) throws Exception {
        
        Thread thread = new Thread(() -> {
            try {
                System.out.println("Thread started");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.out.println("Thread interrupted");
            }
            System.out.println("Hello, World!");
        });
        thread.start();
        thread.join(); //等待线程结束
        System.out.println("Thread finished");

        //定义一个线程池    
        for (int i = 0; i < 10; i++) {
            executorService.submit(() -> {
                System.out.println("Hello, World!");
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Thread finished");



    }


}
