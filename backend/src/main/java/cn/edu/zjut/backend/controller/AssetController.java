package cn.edu.zjut.backend.controller;

import cn.edu.zjut.backend.common.BizException;
import cn.edu.zjut.backend.common.Result;
import cn.edu.zjut.backend.common.ResultCode;
import cn.edu.zjut.backend.dto.AssetBatchResult;
import cn.edu.zjut.backend.dto.AssetUploadResult;
import cn.edu.zjut.backend.dto.AssetView;
import cn.edu.zjut.backend.dto.UploadFile;
import cn.edu.zjut.backend.entity.Document;
import cn.edu.zjut.backend.service.AssetService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产池接口：入库（单个/批量）、列表、详情、临时链接、软删除。
 * <p>
 * 上传权限：三种角色都可以（处理员在处理过程中可补材料）；
 * 删除权限：分配员与管理员。
 * <p>
 * 注意：MultipartFile 的临时文件在请求结束后会被容器清理，
 * 必须在<b>请求线程内</b>读成字节再交给服务层（历史踩坑）。
 */
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /**
     * 单个文件入库。内容重复时返回已有文件并标记 duplicate=true，不报错。
     *
     * @param allowDuplicate true 表示"我知道重复，仍然新建一条记录"
     */
    @PostMapping("/upload")
    public Result<AssetUploadResult> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "category", required = false) String category,
                                            @RequestParam(value = "allowDuplicate", defaultValue = "false")
                                            boolean allowDuplicate) {
        return Result.ok(assetService.upload(toUploadFile(file), category, allowDuplicate));
    }

    /**
     * 批量入库（评测一次灌十几个文件走这里）。单个失败不影响其他文件。
     */
    @PostMapping("/batch")
    public Result<AssetBatchResult> batch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "category", required = false) String category) {
        if (files == null || files.length == 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "请至少选择一个文件");
        }
        List<UploadFile> payload = new ArrayList<>(files.length);
        for (MultipartFile file : files) {
            payload.add(toUploadFile(file));
        }
        return Result.ok(assetService.uploadBatch(payload, category));
    }

    /**
     * 文件池列表：本部门 + 全局共享资产。
     */
    @GetMapping
    public Result<List<AssetView>> list(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(assetService.list(category, keyword));
    }

    @GetMapping("/{id}")
    public Result<AssetView> detail(@PathVariable Long id) {
        Document doc = assetService.getOwned(id);
        return Result.ok(AssetView.from(doc));
    }

    /**
     * 临时下载链接（默认 1 小时）。
     */
    @GetMapping("/{id}/url")
    public Result<String> url(@PathVariable Long id,
                              @RequestParam(value = "expireSeconds", defaultValue = "3600") int expireSeconds) {
        return Result.ok(assetService.presignedUrl(id, expireSeconds));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public Result<Void> delete(@PathVariable Long id) {
        assetService.softDelete(id);
        return Result.ok();
    }

    /**
     * 在请求线程内读取字节，避免异步/后续阶段拿到已被清理的临时文件。
     */
    private UploadFile toUploadFile(MultipartFile file) {
        try {
            return new UploadFile(file.getOriginalFilename(), file.getBytes(), file.getContentType());
        } catch (IOException e) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "读取上传文件失败：" + file.getOriginalFilename() + " (" + e.getMessage() + ")");
        }
    }
}
