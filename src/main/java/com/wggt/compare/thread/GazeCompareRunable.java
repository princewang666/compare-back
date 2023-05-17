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

import com.wggt.compare.dao.GazeMapper;
import com.wggt.compare.dao.ResGazeMapper;
import com.wggt.compare.pojo.Gaze;
import com.wggt.compare.pojo.Path;
import com.wggt.compare.pojo.ResGaze;
import com.wggt.compare.utils.SpringContextUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GazeCompareRunable implements Runnable {
    
    public GazeCompareRunable(Path path) {
        this.path = path;
    }

    private Path path;  // 全链路和软解的文件夹路径
    private int lastFilesNum;  // 上次的文件数
    private List<File> files;  // 用于存放所有找到软解码流文件
    private HashSet<File> hasCompared;  // 用于存放已经比较过的码流

    @Autowired
    // 多线程是安全的，不能直接注入，要调用工具类来注入
    private GazeMapper gazeMapper;

    @Autowired
    private ResGazeMapper resGazeMapper;


    @Override
    public void run() {
        // System.out.println(scanMapper);
        gazeMapper = SpringContextUtils.getApplicationContext().getBean(GazeMapper.class);
        // System.out.println(scanMapper);
        resGazeMapper = SpringContextUtils.getApplicationContext().getBean(ResGazeMapper.class);
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
                int headerLength = 38;  // 只用取一个同步头即可获得全部信息
                byte[] header = new byte[headerLength];
                bis.read(header, 0, headerLength);
                String url = file.getAbsolutePath().replace('\\', '/');
                Short channel = (short) (header[16] & 15);
                Short near = (short) (header[27] & 15);
                Short checkerrMode = (short) (header[28] & 15);
                Integer frameCount =  ((header[33] & 240) >>> 4);
                Gaze temp = new Gaze(null, path.getSrcDirPath(), near, channel, checkerrMode, null, null, null);

                // 3.对比源数据库查找,不找帧编号，将结果全部返回
                List<Gaze> srcLists = gazeMapper.selectExceptFrameCount(temp);
                if (srcLists == null) {
                    // 该帧没找到对应的对比数据，写入结果并continue下一软解码流帧
                    Short res = 6;  // 6 对比源没找到
                    ResGaze resTemp = new ResGaze(null, res, null, url, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                    if (resGazeMapper.selectByUrl(url) == null) {
                        // 将结果文件放入数据库，如果url不在就插入
                        resGazeMapper.insert(resTemp);
                    } else {
                        log.info("已比较过{}",url);
                    }
                    bis.close();
                    continue;
                }

                // 4.遍历先查看是否有帧编号一致的是否只有一个
                int count = 0;
                for (Gaze src : srcLists) {
                    if (src.getFrameCount() == frameCount) {
                        count++;
                    }
                }
                if (count == 1) {
                    // 只比确定的一帧
                    for (Gaze src : srcLists) {
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
                            ResGaze resTemp = new ResGaze(null, resCompare, src.getUrl(), url, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                            if (resGazeMapper.selectByUrl(url) == null) {
                                // 将结果文件放入数据库，如果url不在就插入
                                resGazeMapper.insert(resTemp);
                            } else {
                                log.info("已比较过{}",url);
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
                    for (Gaze src : srcLists) {
                        // 磁盘读取对比源
                        srcBis = new BufferedInputStream(new FileInputStream(src.getUrl()));
                        byte[] srcAll = srcBis.readAllBytes();
                        // 磁盘读取全部软解码流
                        byte[] destAll = bis.readAllBytes();
                        // 5.分模块进行对比
                        Short resCompare = (short) compareByte(srcAll, destAll);

                        if (resCompare == 0) {
                            // 6.写入对比结果进数据库
                            ResGaze resTemp = new ResGaze(null, resCompare, src.getUrl(), url, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                            if (resGazeMapper.selectByUrl(url) == null) {
                                // 将结果文件放入数据库，如果url不在就插入
                                resGazeMapper.insert(resTemp);
                            } else {
                                log.info("已比较过{}",url);
                            }
                            bis.close();
                            srcBis.close();
                            isFind = true;
                            break;
                        }
                    }
                    if (!isFind) {
                        ResGaze resTemp = new ResGaze(null, (short) 6, null, url, near, channel, checkerrMode, frameCount, LocalDateTime.now(), LocalDateTime.now());
                        if (resGazeMapper.selectByUrl(url) == null) {
                            // 将结果文件放入数据库，如果url不在就插入
                            resGazeMapper.insert(resTemp);
                        } else {
                            log.info("已比较过{}",url);
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
        int compressHeaderLength = 266;
        byte[] srcCompressHeader = new byte[compressHeaderLength];
        byte[] destCompressHeader = new byte[compressHeaderLength];
        int cameraHeaderLength = 306;
        byte[] srcCameraHeader = new byte[cameraHeaderLength];
        byte[] destCameraHeader = new byte[cameraHeaderLength];
        int cameraTailerLength = 210;
        byte[] srcCameraTailer = new byte[cameraTailerLength];
        byte[] destCameraTailer = new byte[cameraTailerLength];
        int compressTailerLength = 12;
        byte[] srcCompressTailer = new byte[compressTailerLength];
        byte[] destCompressTailer = new byte[compressTailerLength];
        // 单机出来的码流会补包，所以要先判断一下帧尾（扫描可能也要改）
        if (!(destAll.length >= srcAll.length && isComplete(destAll, srcAll.length))) {
            return 7;  // 大小都不一样，直接不用比了
        }
        int srcCodeLength = srcAll.length - compressHeaderLength - cameraHeaderLength - cameraTailerLength - compressTailerLength;
        byte[] srcCode = new byte[srcCodeLength];
        byte[] destCode = new byte[srcCodeLength];
        // 按块拆分
        int conditon1 = compressHeaderLength;
        int conditon2 = (cameraHeaderLength + compressHeaderLength);
        int conditon3 = (srcCodeLength + cameraHeaderLength + compressHeaderLength);
        int conditon4 = (cameraTailerLength + srcCodeLength + cameraHeaderLength + compressHeaderLength);
        int conditon5 = (compressTailerLength + cameraTailerLength + srcCodeLength + cameraHeaderLength + compressHeaderLength);
        for (int i = 0; i < srcAll.length; i++) {
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
        noCompareCompressHeader(srcCompressHeader, destCompressHeader);
        for (int i = 0; i < compressHeaderLength; i++) {
            if(srcCompressHeader[i] != destCompressHeader[i]) {
                return 1;
            }
        }
        // b.相机头
        // noCompareCameraHeader(srcCameraHeader, destCameraHeader);
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
        noCompareCompressTailer(srcCompressTailer, destCompressTailer);
        for (int i = 0; i < compressTailerLength; i++) {
            if(srcCompressTailer[i] != destCompressTailer[i]) {
                return 5;
            }
        }
        return 0;
    }


    /**
     * 屏蔽压缩头的字节，查相关格式文档
     * @param srcCompressHeader
     * @param destCompressHeader
     */
    public void noCompareCompressHeader(byte[] srcCompressHeader, byte[] destCompressHeader) {
        for (int i = 0; i < 7; i++) {  // 7个同步头屏蔽7次
            srcCompressHeader[17 + (i * 38)] = (byte) 0;  // 数据类型、模式类型屏蔽
            destCompressHeader[17 + (i * 38)] = (byte) 0;  //  数据类型、模式类型屏蔽
    
            srcCompressHeader[18 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[19 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[20 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[21 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[22 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[23 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[24 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            srcCompressHeader[25 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[18 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[19 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[20 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[21 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[22 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[23 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[24 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽
            destCompressHeader[25 + (i * 38)] = (byte) 0;  // 扣块参数屏蔽

            srcCompressHeader[29 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            srcCompressHeader[30 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            srcCompressHeader[31 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            srcCompressHeader[32 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            destCompressHeader[29 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            destCompressHeader[30 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            destCompressHeader[31 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽
            destCompressHeader[32 + (i * 38)] = (byte) 0;  // 压缩帧计数屏蔽

    
            srcCompressHeader[26 + (i * 38)] = (byte) 0;  // 抽帧方式选择屏蔽
            destCompressHeader[26 + (i * 38)] = (byte) 0;  //  抽帧方式选择屏蔽
    
            // 有可能帧编号也是不一样的，再说
        }
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


    public void noCompareCompressTailer(byte[] srcCompressTailer, byte[] destCompressTailer) {
        srcCompressTailer[0] = (byte) 0;  // 压缩时延屏蔽
        srcCompressTailer[1] = (byte) 0;  // 压缩时延屏蔽
        srcCompressTailer[2] = (byte) 0;  // 压缩时延屏蔽
        srcCompressTailer[3] = (byte) 0;  // 压缩时延屏蔽
        destCompressTailer[0] = (byte) 0;  // 压缩时延屏蔽
        destCompressTailer[1] = (byte) 0;  // 压缩时延屏蔽
        destCompressTailer[2] = (byte) 0;  // 压缩时延屏蔽
        destCompressTailer[3] = (byte) 0;  // 压缩时延屏蔽

        srcCompressTailer[4] = (byte) 0;  // 相机和校验屏蔽
        srcCompressTailer[5] = (byte) 0;  // 相机和校验屏蔽
        srcCompressTailer[6] = (byte) 0;  // 相机和校验屏蔽
        srcCompressTailer[7] = (byte) 0;  // 相机和校验屏蔽
        destCompressTailer[4] = (byte) 0;  // 相机和校验屏蔽
        destCompressTailer[5] = (byte) 0;  // 相机和校验屏蔽
        destCompressTailer[6] = (byte) 0;  // 相机和校验屏蔽
        destCompressTailer[7] = (byte) 0;  // 相机和校验屏蔽
    }

    /**
     * 根据压缩帧尾判断是否完整，消除补包的影响
     * @param destAll
     * @param srcLength
     * @return
     */
    public boolean isComplete(byte[] destAll , int srcLength) {
        // byte temp = destAll[srcLength - 1];
        // String see = Integer.toBinaryString(temp);
        if (destAll[srcLength - 1] != 0x57) return false;
        if (destAll[srcLength - 2] != 0x5A) return false;
        if (destAll[srcLength - 3] != 0x53) return false;
        if (destAll[srcLength - 4] != 0x4E) return false;
        return true;
    }
    
}
