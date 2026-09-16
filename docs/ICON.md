# Vulkan 配色封面

以官方 Voxy 的 `assets/voxy/icon.png` 为直接编辑底图，保留 V 字形、网格和
平面图形质感，将绿色改为珊瑚橙红与深酒红。官方绿色原图保留不变。
魔改版为 `src/main/resources/assets/voxy/icon-vulkan.png`。
Fabric 元数据与 Sodium 配置页均引用该图片。

## PCL 显示限制

2026-09-17 检查 PCL 公布的 `MyLocalModItem.xaml.vb`，模组列表在有在线
`Entry.Project` 时使用项目图标，否则使用 `Icons/NoIcon.png`，没有本地 JAR
图标回退。因此内嵌图片不等于未发布的本地开发 JAR 能在 PCL 列表显示封面。
本机只读检查到 PCL Snapshot 2.13.1.1；未修改或启动该 PCL，也未声称完成
其 UI 实测。若后续通过 CurseForge / Modrinth 正式发布，需要设置项目封面，
并让启动器匹配到上传的文件。本次没有发布项目、伪造项目标识或修改 PCL 缓存。

来源：

- [PCL 模组列表实现](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Pages/PageInstance/MyLocalModItem.xaml.vb#L379-L384)
- [Fabric 图标元数据规范](https://docs.fabricmc.net/zh_cn/develop/loader/fabric-mod-json)

## 制作方式与最终提示词

使用内置 image_gen 对官方原图编辑，没有调用外部图像 API。最终提示词：

> Edit the supplied ORIGINAL official Voxy icon by changing its COLORS ONLY. Preserve exactly its distinctive curved V silhouette, flat fills, dark outline, position, original grid geometry and margins. Do not redraw or embellish the logo. Map its luminous green to coral orange (#FF713D), its dark green field to a completely filled opaque dark burgundy (#301018), dark green outline to dark burnt red (#743023), subdued minor grid lines to muted burgundy copper. The entire square background must be solid opaque dark burgundy, with absolutely NO transparency, NO holes, NO black speckles, NO grunge, NO texture, NO noise, NO glowing haze, NO gradients. Smooth clean flat color artwork like the input. Keep the V wholly inside the frame with exactly the original padding, all major grid lines reaching the image boundaries. Only palette changes, no added text or elements. Output a square opaque PNG icon at 1024x1024.
