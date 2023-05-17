package com.wggt.compare.dao;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.wggt.compare.pojo.ResGaze;

@Mapper
public interface ResGazeMapper {

    /**
     * 插入一条数据结果
     * @param resTemp
     */
    @Insert("insert into res_gaze (res, src_url, dest_url, near, channel, checkerr_mode, frame_count, create_time, update_time)" + 
            " values (#{res},#{srcUrl},#{destUrl},#{near},#{channel},#{checkerrMode},#{frameCount},#{createTime},#{updateTime})")
    public void insert(ResGaze resTemp);

    /**
     * 查询当前扫描对比结果
     * @return
     */
    @Select("select * from res_gaze where dest_url like concat(#{destDirPath}, '%')")
    public List<ResGaze> listPre(String destDirPath);

    /**
     * 根据传入的url查询结果数据库数据
     * @param url
     * @return
     */
    @Select("select * from res_gaze where dest_url = #{url}")
    public ResGaze selectByUrl(String url);
}
