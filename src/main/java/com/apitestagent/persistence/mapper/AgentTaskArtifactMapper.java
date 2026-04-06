package com.apitestagent.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.apitestagent.persistence.entity.AgentTaskArtifactEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface AgentTaskArtifactMapper extends BaseMapper<AgentTaskArtifactEntity> {
}