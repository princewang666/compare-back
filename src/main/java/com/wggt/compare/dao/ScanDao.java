package com.wggt.compare.dao;

import java.util.List;

import com.wggt.compare.pojo.Scan;

public interface ScanDao {

    /**
     * 根据文件夹路径取出全部的全链路对比源
     * @param srcDir
     * @return
     * @throws Exception
     */
    public List<Scan> list(String srcDir) throws Exception;

}
