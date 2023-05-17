package com.wggt.compare.pojo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResGaze {
    private Integer id;  // 'ID'
    private Short res;  // '对比结果, 说明: 说明: 0 结果一样， 1 压缩帧头不一样, 2 相机帧头不一样， 3 压缩码流不一样，4 相机帧尾不一样，5 压缩帧尾不一样, 6 对比源没找到, 7 大小不一样
    private String srcUrl;  // '全链路url',
    private String destUrl;  // '，码流url',
    private Short near;  // '微损度, 说明: 0 near=0, 1 near=1, 2 near=2, 3 near=3',
    private Short channel;  // '通道标识, 说明: 0 中波, 3 短波',
    private Short checkerrMode;  // '检纠错模式, 说明: 0 无纠错模式, 1 纠错模式1, 2 纠错模式2',
    private Integer frameCount;  //  '压缩帧计数',    
    private LocalDateTime createTime; //  '创建时间',
    private LocalDateTime updateTime;  // '修改时间'
}
