package com.wggt.compare.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResScan;
import com.wggt.compare.pojo.Result;
import com.wggt.compare.service.ScanService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/scans")
@RestController
public class ScanController {
    
    @Autowired
    private ScanService scanService;  // 扫描服务接口

    /**
     * 查询全部扫描对比结果数据
     * @return
     */
    @GetMapping
    public Result list() {
        log.info("查询扫描全部对比结果数据");
        List<ResScan> resScanList = scanService.list();
        return Result.success(resScanList);
    }

    /**
     * 根据所填的数据，开启对比线程
     * @param path
     * @return
     * @throws Exception
     */
    @PostMapping("/compare")
    public Result compare(@RequestBody Path path) throws Exception {
        log.info("全链路：{}，软解:{},开启对比", path.getSrcDirPath(), path.getDestDirPath());
        scanService.compare(path);
        return Result.success();
    }

    /**
     * 查询当前扫描对比结果数据
     * @return
     */
    @PostMapping("/list/pre")
    public Result listPre(@RequestBody Path path) {
        log.info("查询当前扫描对比结果数据");
        List<ResScan> resScanList = scanService.listPre(path.getDestDirPath());
        return Result.success(resScanList);
    }
}
