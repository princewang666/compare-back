package com.wggt.compare.pojo;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Gaze {
    private Integer id;  // 'ID'
    private String url;  //'码流url',
    private Short near;  // '微损度, 说明: 0 near=0, 1 near=1, 2 near=2, 3 near=3',
    private Short channel;  // '通道标识, 说明: 3 中波, 2 短波',
    private Short checkerrMode;  // '检纠错模式, 说明: 0 无纠错模式, 1 纠错模式1, 2 纠错模式2',
    private Integer frameCount;  //  '压缩帧编号，只用4bit与扫描不同',    
    private LocalDateTime createTime; //  '创建时间',
    private LocalDateTime updateTime;  // '修改时间'
}
