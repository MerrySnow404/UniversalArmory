package com.sighs.universalarmory.gui;

import com.sighs.universalarmory.Universalarmory;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 万用军械库 GUI 控制器。
 *
 * <p>负责管理当前打开的枪械列表、选中枪械、配件编辑状态等。
 * 与 ApricityUI 前端通过数据绑定通信。
 */
public class Index {

    private static final Logger LOGGER = LoggerFactory.getLogger(Index.class);

    /** 前端 HTML 路径 */
    public static final String UI_PATH = "uams/example1.html";

    /** 单例 */
    private static Index instance;

    /** 当前玩家持有的所有枪械条目 */
    private final List<GunEntry> gunEntries = new ArrayList<>();

    /** 当前选中的枪械索引 */
    private int selectedIndex = -1;

    private Index() {
    }

    /**
     * 获取单例。
     */
    @NotNull
    public static Index getInstance() {
        if (instance == null) {
            instance = new Index();
        }
        return instance;
    }

    // ============================================================
    //  扫描玩家背包
    // ============================================================

    /**
     * 扫描玩家背包中所有的枪械，刷新列表。
     *
     * @param player 玩家
     * @return 发现的枪械数量
     */
    public int scanInventory(@NotNull Player player) {
        gunEntries.clear();
        selectedIndex = -1;

        int index = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (AttachmentUtils.isGun(stack)) {
                gunEntries.add(new GunEntry(index, stack));
                index++;
            }
        }

        LOGGER.debug("Scanned inventory, found {} guns", gunEntries.size());
        return gunEntries.size();
    }

    // ============================================================
    //  选中管理
    // ============================================================

    /**
     * 选中指定索引的枪械。
     *
     * @param index 枪械列表中的索引
     * @return 选中是否成功
     */
    public boolean select(int index) {
        if (index < 0 || index >= gunEntries.size()) {
            this.selectedIndex = -1;
            return false;
        }
        this.selectedIndex = index;
        return true;
    }

    /**
     * 获取当前选中的枪械条目。
     */
    @Nullable
    public GunEntry getSelected() {
        if (selectedIndex < 0 || selectedIndex >= gunEntries.size()) {
            return null;
        }
        return gunEntries.get(selectedIndex);
    }

    /**
     * 获取当前选中的枪械 ItemStack。
     */
    @Nullable
    public ItemStack getSelectedStack() {
        GunEntry entry = getSelected();
        return entry != null ? entry.getGunStack() : null;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    // ============================================================
    //  列表查询
    // ============================================================

    /**
     * 获取所有枪械条目。
     */
    @NotNull
    public List<GunEntry> getGunEntries() {
        return Collections.unmodifiableList(gunEntries);
    }

    /**
     * 获取枪械总数。
     */
    public int getGunCount() {
        return gunEntries.size();
    }

    /**
     * 判断列表是否为空。
     */
    public boolean isEmpty() {
        return gunEntries.isEmpty();
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 刷新所有条目的缓存数据（当枪械 NBT 可能变化时调用）。
     */
    public void refreshAll() {
        for (GunEntry entry : gunEntries) {
            entry.refresh();
        }
    }

    /**
     * 将当前状态序列化为前端可用的 Map。
     */
    @NotNull
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gunCount", gunEntries.size());
        data.put("selectedIndex", selectedIndex);

        List<Map<String, Object>> gunList = new ArrayList<>();
        for (GunEntry entry : gunEntries) {
            gunList.add(entry.toMap());
        }
        data.put("guns", gunList);

        GunEntry selected = getSelected();
        data.put("hasSelected", selected != null);
        if (selected != null) {
            data.put("selected", selected.toMap());
        }

        return data;
    }
}
