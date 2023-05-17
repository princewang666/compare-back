package com.wggt.compare.service;

import java.util.List;

import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResScan;

public interface ScanService {
    /**
     * 查询全部扫描对比结果
     * @return
     */
    public List<ResScan> list();

    /**
     * 根据传入的全链路和软解路径进行对比
     * @param path
     * @throws Exception
     */
    public void compare(Path path) throws Exception;

    
    /**
     * 查询当前软件路径的扫描结果
     * @param destDirPath
     * @return
     */
    public List<ResScan> listPre(String destDirPath);
}
