package com.wggt.compare.dao.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.wggt.compare.dao.GazeDao;
import com.wggt.compare.pojo.Gaze;

@Repository
public class GazeDaoImpl implements GazeDao{
    private List<File> files;

    @Override
    public List<Gaze> list(String srcDir) throws Exception {
        // 根据传入的文件夹路径取出所有全链路对比源的路径
        files = new ArrayList<>();
        File dir = new File(srcDir);
        String fileName = "组帧后码流.src";
        searchFile(dir, fileName);
        // 此时files已经得到所有的文件路径，组装进行返回
        List<Gaze> res = files.size() == 0 ? null : new ArrayList<>();
        for (File file : files) {
            // 打开文件路径所对应的文件
            Gaze temp = extractGazeHeader(file);
            res.add(temp);
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

    /**
     * 根据传入的文件名提取凝视头
     * @param file
     * @return
     * @throws IOException
     */
    public Gaze extractGazeHeader(File file) throws IOException {
        // 打开文件路径所对应的文件
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        int headerLength = 38;  // 只用取一个同步头即可获得全部信息
        byte[] header = new byte[headerLength];
        bis.read(header, 0, headerLength);
        String url = file.getAbsolutePath().replace('\\', '/');
        Short channel = (short) (header[16] & 15);
        Short near = (short) (header[27] & 15);
        Short checkerrMode = (short) (header[28] & 15);
        Integer frameCount =  ((header[33] & 240) >>> 4);
        LocalDateTime createTime = LocalDateTime.now();
        LocalDateTime updateTime = LocalDateTime.now();
        Gaze temp = new Gaze(null, url, near, channel, checkerrMode, frameCount, createTime, updateTime);
        bis.close();
        return temp;
    }
}
