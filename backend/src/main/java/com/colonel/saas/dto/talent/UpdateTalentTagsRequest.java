package com.colonel.saas.dto.talent;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateTalentTagsRequest {

  @Size(max = 3, message = "达人标签最多 3 个")
  private List<@Size(max = 50, message = "单个标签最多 50 个字符") String> tags;
}
