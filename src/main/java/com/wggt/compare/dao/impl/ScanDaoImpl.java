package com.wggt.compare.dao.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.lang.Exception;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.wggt.compare.dao.ScanDao;
import com.wggt.compare.pojo.Scan;

@Repository
public class ScanDaoImpl implements ScanDao{
    private List<File> files;  // 用于存放所有找到的文件

    @Override
    public List<Scan> list(String srcDir) throws Exception {
        // 根据传入的文件夹路径取出所有全链路对比源的路径
        files = new ArrayList<>();
        File dir = new File(srcDir);
        String fileName = "组帧后码流.src";
        searchFile(dir, fileName);
        // 此时files已经得到所有的文件路径，组装进行返回
        List<Scan> res = files.size() == 0 ? null : new ArrayList<>();
        for (File file : files) {
            // 打开文件路径所对应的文件
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            int headerLength = 81;  // 只用取一个同步头即可获得全部信息
            byte[] header = new byte[headerLength];
            bis.read(header, 0, headerLength);
            String url = file.getAbsolutePath().replace('\\', '/');
            Short subRow = (short) ((header[16] & 60) >>> 4);
            Short subCol = (short) ((header[16] & 12) >>> 2);
            Short near = (short) ((header[17] & 192) >>> 6);
            Short channel = (short) ((header[17] & 48) >>> 4);
            Short checkerrMode = (short) ((header[18] & 192) >>> 6);
            Integer frameCount =  (header[19] << 24) + (header[20] << 16) + (header[21] << 8) + (header[22]);
            LocalDateTime createTime = LocalDateTime.now();
            LocalDateTime updateTime = LocalDateTime.now();
            Scan temp = new Scan(null, url, subRow, subCol, near, channel, checkerrMode, frameCount, createTime, updateTime);
            res.add(temp);
            bis.close();
        }
        return res;
    }

    /**
     * 递归找出传入父文件夹中的全部符合的文件名路径
     * @param dir
     * @param fileName
     */
    public void searchFile(File dir, String fileName) {
        if (dir == null || !dir.exists()) {
            return;
        }
        if (dir.isFile()) {
            if (dir.getName().equals(fileName)) {
                files.add(dir);
            }
            return;
        }
        // 是文件夹，取出所有递归查找
        File[] oneDirs = dir.listFiles();
        if (oneDirs != null && oneDirs.length > 0) {
            for (File oneDir : oneDirs) {
                if (oneDir.isDirectory()) {
                    searchFile(oneDir, fileName);
                } else {
                    if (oneDir.getName().equals(fileName)) {
                        files.add(oneDir);
                    }
                }
            }
        }
    }
    
}
