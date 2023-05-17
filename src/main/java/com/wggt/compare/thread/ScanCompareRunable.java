package com.wggt.compare.thread;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.wggt.compare.dao.ResScanMapper;
import com.wggt.compare.dao.ScanMapper;
import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResScan;
import com.wggt.compare.pojo.Scan;
import com.wggt.compare.utils.SpringContextUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScanCompareRunable implements Runnable{

    public ScanCompareRunable(Path path) {
        this.path = path;
    }

    private Path path;  // 全链路和软解的文件夹路径
    private int lastFilesNum;  // 上次的文件数
    private List<File> files;  // 用于存放所有找到软解码流文件
    private HashSet<File> hasCompared;  // 用于存放已经比较过的码流

    @Autowired
    // 多线程是安全的，不能直接注入，要调用工具类来注入
    private ScanMapper scanMapper;

    @Autowired
    private ResScanMapper resScanMapper;


    @Override
    public void run() {
        // System.out.println(scanMapper);
        scanMapper = SpringContextUtils.getApplicationContext().getBean(ScanMapper.class);
        // System.out.println(scanMapper);
        resScanMapper = SpringContextUtils.getApplicationContext().getBean(ResScanMapper.class);
        // System.out.println(resScanMapper);
        lastFilesNum = 0;  // 每次开启线程先把上次的文件数设为0
        files = new ArrayList<>();  // 新建
        hasCompared = new HashSet<>();  // 新建
        long time = 0l;  // 计时关闭线程
        long start = System.currentTimeMillis();  // 计时
        // 超过1min就结束线程
        while (time < (1000 * 60)) {
            // 检查数据是否有发生变化
            File destDir = new File(path.getDestDirPath());
            if (!isIncrease(destDir)) {
                time = System.currentTimeMillis() - start;
                continue;
            }

            // 数据有变化，遍历读取新软解数据放入files（全取完再比）
            getAllDest(destDir);

            // 所有压缩码流都在files里，取出来进行对比（全比完再清空）
            compareResSave();

            start = System.currentTimeMillis();
        }
        // 在结束前再检查一次所有文件，防止有对比数据遗漏
        getAllDest(new File(path.getDestDirPath()));

        compareResSave();

        log.info("文件夹监听超时，对比线程结束");
    }

    /**
     * 对比files中的所有文件，结束时清空，并将结果写入数据库
     */
    public void compareResSave() {
        // 所有压缩码流都在files里，取出来进行对比（全比完再清空）
        for (File file : files) {
            try {
                // 1.磁盘读取软解码流文件
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                // 2.码流头解析
                int headerLength = 81;  // 只用取一个同步头即可获得全部信息
                byte[] header = new byte[headerLength];
                bis.read(header, 0, headerLength);
                String url = path.getSrcDirPath().replace('\\', '/');
                Short subRow = (short) ((header[16] & 60) >>> 4);
                Short subCol = (short) ((header[16] & 12) >>> 2);
                Short near = (short) ((header[17] & 192) >>> 6);
                Short channel = (short) ((header[17] & 48) >>> 4);
                Short checkerrMode = (short) ((header[18] & 192) >>> 6);
                Integer frameCount =  (header[19] << 24) + (header[20] << 16) + (header[21] << 8) + (header[22]);
                Scan temp = new Scan(null, url, subRow, subCol, near, channel, checkerrMode, null, null, null);

                // 3.对比源数据库查找,不找帧编号，将结果全部返回
                List<Scan> srcLists = scanMapper.selectAll(temp);
                if (srcLists == null) {
                    // 该帧没找到对应的对比数据，写入结果并continue下一软解码流帧
                    Short res = 6;  // 6 对比源没找到
                    ResScan resTemp = new ResScan(null, res, null, file.getAbsolutePath().replace('\\', '/'), subRow, subCol, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                    try {
                        // 将对比结果放入数据库，如果软解文件已经比过就不放了
                        resScanMapper.insert(resTemp);
                    } catch (Exception e) {
                        log.info("已比较过{}",file.getAbsolutePath().replace('\\', '/'));
                    }
                    bis.close();
                    continue;
                }

                // 4.遍历先查看是否有帧编号一致的是否只有一个
                int count = 0;
                for (Scan src : srcLists) {
                    if (src.getFrameCount() == frameCount) {
                        count++;
                    }
                }
                if (count == 1) {
                    // 只比确定的一帧
                    for (Scan src : srcLists) {
                        if (src.getFrameCount() == frameCount) {
                            // 磁盘读取对比源
                            BufferedInputStream srcBis = new BufferedInputStream(new FileInputStream(src.getUrl()));
                            byte[] srcAll = srcBis.readAllBytes();
                            // 磁盘读取全部软解码流
                            // 前面已经读了头大小的文件，需要流已经回不去了，需要重新建立一个
                            bis = new BufferedInputStream(new FileInputStream(file));
                            byte[] destAll = bis.readAllBytes();
                            // 5.分模块进行对比
                            Short resCompare = (short) compareByte(srcAll, destAll);

                            // 6.写入对比结果进数据库
                            ResScan resTemp = new ResScan(null, resCompare, src.getUrl(), file.getAbsolutePath().replace('\\', '/'), subRow, subCol, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                            try {
                                // 将对比结果放入数据库，如果软解文件已经比过就不放了
                                resScanMapper.insert(resTemp);
                            } catch (Exception e) {
                                log.info("已比较过{}",file.getAbsolutePath().replace('\\', '/'));
                            }
                            
                            bis.close();
                            srcBis.close();
                            break;
                        }
                    }
                } else {
                    // 帧编号无效，遍历比，看能不能找到完全一样的
                    boolean isFind = false;
                    BufferedInputStream srcBis = null;
                    for (Scan src : srcLists) {
                        // 磁盘读取对比源
                        srcBis = new BufferedInputStream(new FileInputStream(src.getUrl()));
                        byte[] srcAll = srcBis.readAllBytes();
                        // 磁盘读取全部软解码流
                        byte[] destAll = bis.readAllBytes();
                        // 5.分模块进行对比
                        Short resCompare = (short) compareByte(srcAll, destAll);

                        if (resCompare == 0) {
                            // 6.写入对比结果进数据库
                            ResScan resTemp = new ResScan(null, resCompare, src.getUrl(), file.getAbsolutePath().replace('\\', '/'), subRow, subCol, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                            try {
                                // 将对比结果放入数据库，如果软解文件已经比过就不放了
                                resScanMapper.insert(resTemp);
                            } catch (Exception e) {
                                log.info("已比较过{}",file.getAbsolutePath().replace('\\', '/'));
                            }
                            bis.close();
                            srcBis.close();
                            isFind = true;
                            break;
                        }
                    }
                    if (!isFind) {
                        ResScan resTemp = new ResScan(null, (short) 6, null, file.getAbsolutePath().replace('\\', '/'), subRow, subCol, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                        try {
                            // 将对比结果放入数据库，如果软解文件已经比过就不放了
                            resScanMapper.insert(resTemp);
                        } catch (Exception e) {
                            log.info("已比较过{}",file.getAbsolutePath().replace('\\', '/'));
                        }
                        bis.close();
                        srcBis.close();
                    }
                }


            } catch (IOException e) {
                log.info("文件打开错误");
                e.printStackTrace();
            }
        }
        
        // 把所有已经对比过的都清空了
        files.clear();
    }

    /**
     * 监听文件夹是否发生变化
     * @param destDir
     * @return
     */
    public boolean isIncrease(File destDir) {
        // 检查数据是否有发生变化,这个检查是非常快的，当许多文件夹不同步进来是，
        // 可能会在第一次只检测到部分文件夹变化
        File[] oneDirs = destDir.listFiles();
        if (oneDirs == null || oneDirs.length == lastFilesNum) {
            return false;
        } else {
            lastFilesNum = oneDirs.length;
            return true;
        }
    }

    /**
     * 遍历读取新软解数据放到files里（全取完）
     * @param destDir
     */
    public void getAllDest(File destDir) {
        // 数据有变化，遍历读取新软解数据放到files里（全取完）
        File[] oneDirs = destDir.listFiles();
        /*
         * listFiles默认是按名称排序的，跟windows文件下看到的大致相同，但有些区别，以如下为例
         * "扫描短波_04月03日10时01分49秒_解码数据\00实时"
         * "扫描短波_04月03日10时01分49秒_解码数据\01地标"
         * "扫描短波_04月03日10时01分49秒_解码数据\02背景"
         * "扫描短波_04月03日10时01分49秒_解码数据\SrcCircularBuffer"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波04月03日10时01分_解码记录.txt"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波04月03日10时01分_解码错误.txt"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000.src"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000000000"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000000001"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000000002"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000000003"
         * "扫描短波_04月03日10时01分49秒_解码数据\扫描短波_0000000004"
         * "扫描短波_04月03日10时01分49秒_解码数据\解码输出表_扫描2023年 04月 03日 10时 03分 04秒.csv"
         * 
         * 提取码流思路：
         * 因为来的文件夹可能乱序，也可能里面还没来得及生成码流，
         * 所以每次提取都进行遍历，如果没有解码过就放入files
         */
        for (int i = 0; i < oneDirs.length; i++) {
            if (oneDirs[i].isDirectory() && isSrcDir(oneDirs[i].getName())) {
                // 是文件夹且为里面可能有码流的
                // 文件夹，二级目录下可能有压缩码流
                File[] twoDirs = oneDirs[i].listFiles();
                // 子文件下存在文件
                if (twoDirs != null && twoDirs.length != 0) {
                    for (File twoDir : twoDirs) {
                        if (twoDir.isFile() && twoDir.getName().equals("压缩码流.src")) {
                            if (hasCompared.add(twoDir)) {
                                files.add(twoDir);
                            }
                            break;
                        }
                    }
                }                    
            }
        }
    }

    /**
     * 根据传入的文件夹名判断是否为要用的（尾缀数字）
     * @param name
     * @return
     */
    public boolean isSrcDir(String name) {
        char end = name.charAt(name.length() - 1);
        if (end >= '0' && end <= '9') {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 比较软解和全链路的码流文件
     * @param srcAll
     * @param destAll
     * @return
     */
    public int compareByte(byte[] srcAll, byte[] destAll) {
        // 按块拆分，分模块对比
        int compressHeaderLength = 567;
        byte[] srcCompressHeader = new byte[compressHeaderLength];
        byte[] destCompressHeader = new byte[compressHeaderLength];
        int cameraHeaderLength = 800;
        byte[] srcCameraHeader = new byte[cameraHeaderLength];
        byte[] destCameraHeader = new byte[cameraHeaderLength];
        int cameraTailerLength = 200;
        byte[] srcCameraTailer = new byte[cameraTailerLength];
        byte[] destCameraTailer = new byte[cameraTailerLength];
        int compressTailerLength = 20;
        byte[] srcCompressTailer = new byte[compressTailerLength];
        byte[] destCompressTailer = new byte[compressTailerLength];
        int paddingCodeLength = 875;
        int srcCodeLength = srcAll.length - compressHeaderLength - cameraHeaderLength - cameraTailerLength - compressTailerLength - paddingCodeLength;
        int destCodeLength = destAll.length - compressHeaderLength - cameraHeaderLength - cameraTailerLength - compressTailerLength - paddingCodeLength;
        if (srcCodeLength != destCodeLength) {
            // 大小都不一样，直接不用比了
            return 7;
        }
        byte[] srcCode = new byte[srcCodeLength];
        byte[] destCode = new byte[srcCodeLength];
        // 按块拆分
        int conditon1 = compressHeaderLength;
        int conditon2 = (cameraHeaderLength + compressHeaderLength);
        int conditon3 = (srcCodeLength + cameraHeaderLength + compressHeaderLength);
        int conditon4 = (cameraTailerLength + srcCodeLength + cameraHeaderLength + compressHeaderLength);
        int conditon5 = (compressTailerLength + cameraTailerLength + srcCodeLength + cameraHeaderLength + compressHeaderLength);
        for (int i = 0; i < srcAll.length - paddingCodeLength; i++) {
            if (i < conditon1) {
                srcCompressHeader[i] = srcAll[i];
                destCompressHeader[i] = destAll[i];
            } else if (i < conditon2) {
                srcCameraHeader[i - conditon1] = srcAll[i];
                destCameraHeader[i - conditon1] = destAll[i];
            } else if (i < conditon3) {
                srcCode[i - conditon2] = srcAll[i];
                destCode[i - conditon2] = destAll[i];
            } else if (i < conditon4) {
                srcCameraTailer[i - conditon3] = srcAll[i];
                destCameraTailer[i - conditon3] = destAll[i];
            } else if (i < conditon5) {
                srcCompressTailer[i - conditon4] = srcAll[i];
                destCompressTailer[i - conditon4] = destAll[i];
            }
        }
        // 分块对比
        // a.压缩头
        for (int i = 0; i < compressHeaderLength; i++) {
            if(srcCompressHeader[i] != destCompressHeader[i]) {
                return 1;
            }
        }
        // b.相机头
        noCompareCameraHeader(srcCameraHeader, destCameraHeader);
        for (int i = 0; i < cameraHeaderLength; i++) {
            if(srcCameraHeader[i] != destCameraHeader[i]) {
                return 2;
            }
        }
        // c.码流
        for (int i = 0; i < srcCodeLength; i++) {
            if(srcCode[i] != destCode[i]) {
                return 3;
            }
        }
        // d.相机尾
        for (int i = 0; i < cameraTailerLength; i++) {
            if(srcCameraTailer[i] != destCameraTailer[i]) {
                return 4;
            }
        }
        // e.压缩尾
        for (int i = 0; i < compressTailerLength; i++) {
            if(srcCompressTailer[i] != destCompressTailer[i]) {
                return 5;
            }
        }
        return 0;
    }

    /**
     * 屏蔽相机头的字节，查相关格式文档
     * @param srcCameraHeader
     * @param destCameraHeader
     */
    public void noCompareCameraHeader(byte[] srcCameraHeader, byte[] destCameraHeader) {
        // byte temp = srcCameraHeader[24];
        // String see = Integer.toBinaryString(temp);
        srcCameraHeader[24] = (byte) (srcCameraHeader[24] & 15);  //前半场波段（短、中）标识屏蔽
        destCameraHeader[24] = (byte) (destCameraHeader[24] & 15);  //前半场波段（短、中）标识屏蔽
        // int temp2 = srcCameraHeader[424];
        srcCameraHeader[424] = (byte) (srcCameraHeader[424] & 15);  //后半场波段（短、中）标识屏蔽
        destCameraHeader[424] = (byte) (destCameraHeader[424] & 15);  //后半场波段（短、中）标识屏蔽
    }
    
}
