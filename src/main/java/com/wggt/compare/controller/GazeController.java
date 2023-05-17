package com.wggt.compare.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResGaze;
import com.wggt.compare.pojo.Result;
import com.wggt.compare.service.GazeService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/gazes")
@RestController
public class GazeController {
    
    @Autowired
    private GazeService gazeService;  // 凝视服务接口

    /**
     * 根据所填的数据，开启对比线程
     * @param path
     * @return
     * @throws Exception
     */
    @PostMapping("/compare")
    public Result compare(@RequestBody Path path) throws Exception {
        log.info("全链路：{}，软解:{},开启对比", path.getSrcDirPath(), path.getDestDirPath());
        gazeService.compare(path);
        return Result.success();
    }

    /**
     * 查询当前凝视对比结果数据
     * @return
     */
    @PostMapping("/list/pre")
    public Result listPre(@RequestBody Path path) {
        log.info("查询当前凝视对比结果数据");
        List<ResGaze> resGazeList = gazeService.listPre(path.getDestDirPath());
        return Result.success(resGazeList);
    }
}
