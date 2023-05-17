package com.wggt.compare.service;

import java.util.List;

import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResGaze;

public interface GazeService {
    /**
     * 根据传入的全链路和软解路径进行对比
     * @param path
     * @throws Exception
     */
    public void compare(Path path) throws Exception;

    /**
     * 查询当前全部扫描对比结果
     * @return
     */
    public List<ResGaze> listPre(String destDirPath);
}
