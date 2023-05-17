package com.wggt.compare.service.impl;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wggt.compare.dao.ResScanMapper;
import com.wggt.compare.dao.ScanDao;
import com.wggt.compare.dao.ScanMapper;
import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResScan;
import com.wggt.compare.pojo.Scan;
import com.wggt.compare.service.ScanService;
import com.wggt.compare.thread.ScanCompareRunable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ScanServiceImpl implements ScanService{

    // 服务类@Service是单例，创建属性线程池，当调用compare方法就交给线程池做
    ExecutorService pool = new ThreadPoolExecutor(3, 5, 6, TimeUnit.SECONDS, 
    new ArrayBlockingQueue<>(5), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    private ResScanMapper resScanMapper;  // 对比结果数据库服务接口

    @Autowired
    private ScanDao scanDao;  // 全链路文件夹读取接口

    @Autowired
    private ScanMapper scanMapper;  // 全链路数据库服务接口

    @Override
    public List<ResScan> list() {
        // 直接调用查询语句返回
        return resScanMapper.list();
    }

    @Override
    public void compare(Path path) throws Exception {
        // 先将全链路文件取出
        List<Scan> srcLists = scanDao.list(path.getSrcDirPath());
        if (srcLists == null) {
            log.info("在所给文件夹路径下找不到对比码流");
        } else {
            try {
                // 将全链路文件放入数据库待查，如果url已经在了就不放了
                scanMapper.save(srcLists);
            } catch (Exception e) {
                log.info("数据库中已存在所选数据");
            }
        }
       
        
        // 创建多线程任务开始对比
        log.info("开始对比");
		Runnable target = new ScanCompareRunable(path);
        pool.execute(target);
    }

    @Override
    public List<ResScan> listPre(String destDirPath) {
        return resScanMapper.listPre(destDirPath);
    }
}
