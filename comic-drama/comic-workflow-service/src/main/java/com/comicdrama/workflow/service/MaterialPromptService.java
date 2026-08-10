package com.comicdrama.workflow.service;

import com.comicdrama.workflow.entity.MaterialPrompt;
import com.comicdrama.workflow.mapper.MaterialPromptMapper;
import org.springframework.stereotype.Service;

@Service
public class MaterialPromptService extends AbstractWorkflowService<MaterialPromptMapper, MaterialPrompt> {
}
