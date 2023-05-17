package com.wggt.compare.service.impl;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wggt.compare.dao.GazeDao;
import com.wggt.compare.dao.GazeMapper;
import com.wggt.compare.dao.ResGazeMapper;
import com.wggt.compare.pojo.Gaze;
import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResGaze;
import com.wggt.compare.service.GazeService;
import com.wggt.compare.thread.GazeCompareRunable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GazeServiceImpl implements GazeService{


    // 服务类@Service是单例，创建属性线程池，当调用compare方法就交给线程池做
    ExecutorService pool = new ThreadPoolExecutor(3, 5, 6, TimeUnit.SECONDS, 
    new ArrayBlockingQueue<>(5), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    private ResGazeMapper resGazeMapper;

    @Autowired
    private GazeDao gazeDao;

    @Autowired
    private GazeMapper gazeMapper;

    @Override
    public void compare(Path path) throws Exception {
        // 先将全链路文件取出
        List<Gaze> srcLists = gazeDao.list(path.getSrcDirPath());
        if (srcLists == null) {
            log.info("在所给文件夹路径下找不到对比码流");
        } else {
            // 凝视采取逐个检查url，没有再插入
            for (Gaze srcEle : srcLists) {
                if (gazeMapper.selectByUrl(srcEle.getUrl()) == null) {
                    // 将全链路文件放入数据库待查，如果url不在就插入
                    gazeMapper.insert(srcEle);
                } else {
                    log.info("数据库中已存在所选数据");
                }
            }
        }

        // 创建多线程任务开始对比
        log.info("开始对比");
		Runnable target = new GazeCompareRunable(path);
        pool.execute(target);
    }

    @Override
    public List<ResGaze> listPre(String destDirPath) {
        return resGazeMapper.listPre(destDirPath);
    }

}
