package com.wggt.compare.dao;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.wggt.compare.pojo.ResScan;

@Mapper
public interface ResScanMapper {

    /**
     * 查询全部扫描对比结果
     * @return
     */
    @Select("select * from res_scan")
    public List<ResScan> list();

    /**
     * 插入一条数据结果
     * @param resTemp
     */
    @Insert("insert into res_scan (res, src_url, dest_url, sub_row, sub_col, near, channel, checkerr_mode, frame_count, create_time, update_time)" + 
            " values (#{res},#{srcUrl},#{destUrl},#{subRow},#{subCol},#{near},#{channel},#{checkerrMode},#{frameCount},#{createTime},#{updateTime})")
    public void insert(ResScan resTemp);

    /**
     * 查询当前扫描对比结果
     * @return
     */
    @Select("select * from res_scan where dest_url like concat(#{destDirPath}, '%')")
    public List<ResScan> listPre(String destDirPath);
}
