package com.sighs.universalarmory.gui;

import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.ui.ContextMenu;
import com.sighs.universalarmory.apricity.element.GunShowElement;
import com.sighs.universalarmory.armory.ArmoryClientState;
import com.sighs.universalarmory.armory.ArmoryEntries;
import com.sighs.universalarmory.armory.ArmoryEntry;
import com.sighs.universalarmory.armory.ArmoryItemIdentity;
import com.sighs.universalarmory.armory.ArmoryManager;
import com.sighs.universalarmory.armory.ArmoryNetwork;
import com.sighs.universalarmory.armory.ArmoryQuery;
import com.sighs.universalarmory.favorite.Favorites;
import com.sighs.universalarmory.favorite.RecentViews;
import com.sighs.universalarmory.template.GunTemplateCodecs;
import com.sighs.apricityui.ui.DialogWindow;
import com.sighs.apricityui.ui.ToastManager;
import com.sighs.universalarmory.armory.ArmoryRecipeQueries;
import com.sighs.universalarmory.gui.util.AttachmentUtils;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.item.AttachmentItem;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class ArmoryScreen extends ApricityScreen {
    private Document document;
    private long documentGeneration = -1;
    private long inventoryGeneration = -1;
    private final Deque<Route> history = new ArrayDeque<>();
    private Route route = new Route(Page.HOME, null, 0);
    private View view = View.GRID;
    private boolean grouped;
    private boolean filterOpen;
    private boolean selectMode;
    private final Set<String> selectedKeys = new LinkedHashSet<>();
    private GroupBy groupBy = GroupBy.TYPE;
    private final Set<String> collapsedGroups = new LinkedHashSet<>();
    private ArmoryQuery.Filter filter = ArmoryQuery.Filter.all();
    private ArmoryQuery.Sort sort = ArmoryQuery.Sort.NAME;
    private boolean descending;
    private int pageSize = 6;
    private int detailTab;
    private int attachmentSlot;
    private int resultsScroll;
    private int depositPage;
    private int materialPage;
    private int viewportHeight;




    private long randomSeed = System.nanoTime();
    private boolean ended;
    private List<ArmoryEntry> catalogue = List.of();
    private CompoundTag requestedOptions;
    private int requestedPage = -1;
    private ItemStack previewStack;
    private String detailCodeDraft = "";
    private boolean equipmentPending;
    private DialogWindow equipmentDialog;
    private long handledOperation = -1;

    public ArmoryScreen() {
        super("uams/armory.html");
    }

    public void resetBoundView() {
        closeEquipmentDialog();
        history.clear();
        route = new Route(Page.HOME, null, 0);
        requestedOptions = null;
        requestedPage = -1;
        equipmentPending = false;
        previewStack = null;
        detailCodeDraft = "";
        renderPage();
    }

    @Override
    protected void init() {
        super.init();
        synchronizeDocument();
    }

    @Override
    public void tick() {
        super.tick();
        synchronizeDocument();
        if (document != null && viewportHeight != (int) document.getViewportSize().height()) {
            viewportHeight = (int) document.getViewportSize().height();
            renderPage();
        }
        if (document != null && inventoryGeneration != ArmoryClientState.generation()) {
            inventoryGeneration = ArmoryClientState.generation();
            equipmentPending = false;
            CompoundTag snapshot = ArmoryClientState.snapshot();
            long response = snapshot.getLong("ResponseId");
            if (snapshot.contains("OperationResult") && handledOperation != response) {
                handledOperation = response;
                CompoundTag result = snapshot.getCompound("OperationResult");
                ItemStack stack = ItemStack.of(result.getCompound("Item"));
                if (route.page == Page.DETAIL && ArmoryItemIdentity.kind(stack) == ArmoryItemIdentity.Kind.GUN) {
                    CompoundTag focused = snapshot.getCompound("Focused");
                    route = new Route(Page.DETAIL, new ArmoryEntry(result.getString("Key"), stack,
                            new BigInteger(focused.getString("Quantity")), List.of(result.getString("Id")),
                            ArmoryEntry.Origin.INVENTORY, -1), 0);
                    previewStack = null;
                    detailCodeDraft = "";
                    RecentViews.record(stack);
                }
                ToastManager.show("操作完成：" + stack.getHoverName().getString() + " ×" + result.getString("Quantity"));
            }
            if (snapshot.contains("Notice")) {
                ToastManager.show(snapshot.getString("Notice"));
            }
            renderPage();
        }
    }

    private void synchronizeDocument() {
        Document linked = getLinkedDocument();
        if (linked == null) {
            return;
        }
        if (document != linked || documentGeneration != linked.getRefreshGeneration()) {
            document = linked;
            documentGeneration = linked.getRefreshGeneration();
            Document.runWithContext(document, () -> {
                bindControls();
                renderPage();
            });
        }
    }

    private void bindControls() {
        action("nav-home", () -> navigate(Page.HOME));
        action("nav-inventory", () -> navigate(Page.INVENTORY));
        action("nav-catalogue", () -> navigate(Page.CATALOGUE));
        action("close", this::onClose);
        action("back", () -> {
            if (!equipmentPending && !history.isEmpty()) {
                closeEquipmentDialog();
                previewStack = null;
                detailCodeDraft = "";
                route = history.pop();
                renderPage();
            }
        });
        action("refresh", () -> {
            if (!equipmentPending) {
                randomSeed = System.nanoTime();
                catalogue = List.of();
                requestedOptions = null;
                renderPage();
            }
        });
    }

    private void navigate(Page page) {
        if (equipmentPending) {
            return;
        }
        closeEquipmentDialog();
        previewStack = null;
        detailCodeDraft = "";
        resultsScroll = 0;
        selectMode = false;
        selectedKeys.clear();
        history.push(route);
        route = new Route(page, null, 0);
        renderPage();
    }

    private void changePage(int direction) {
        if (!equipmentPending) {
            resultsScroll = 0;
            route = new Route(route.page, route.entry, Math.max(0, route.index + direction));
            renderPage();
        }
    }

    private void requestQuery() {
        if (equipmentPending || ArmoryClientState.position() == null) {
            return;
        }
        boolean browser = route.page != Page.HOME && route.page != Page.DETAIL && route.page != Page.IMPORT;
        ArmoryQuery.Sort order = route.page == Page.RECENT && sort == ArmoryQuery.Sort.NAME ? ArmoryQuery.Sort.RECENT : sort;
        CompoundTag options = com.sighs.universalarmory.armory.ArmoryQueryCodec.encode(
                browser ? filter : ArmoryQuery.Filter.all(), browser ? order : ArmoryQuery.Sort.NAME,
                browser && descending, route.page == Page.HOME ? 4 : pageSize);
        String source = route.page == Page.DETAIL || route.page == Page.IMPORT ? "INVENTORY" : route.page.name();
        options.putString("Source", source);
        if (browser && filter.favoriteOnly()) {
            net.minecraft.nbt.ListTag keys = new net.minecraft.nbt.ListTag();
            Favorites.global().getFavorites().stream()
                    .flatMap(stack -> java.util.stream.Stream.of(ArmoryItemIdentity.key(stack), ArmoryItemIdentity.resourceKey(stack)))
                    .distinct().sorted().forEach(key -> keys.add(net.minecraft.nbt.StringTag.valueOf(key)));
            options.put("FavoriteKeys", keys);
        }
        if (route.page == Page.HOME) {
            options.putLong("RandomSeed", randomSeed);
            options.put("FavoritePreviews", previews(Favorites.global().getFavorites()));
            options.put("RecentPreviews", previews(RecentViews.items()));
        } else if (route.page == Page.FAVORITE || route.page == Page.RECENT) {
            options.put("Previews", previews(route.page == Page.FAVORITE ? Favorites.global().getFavorites() : RecentViews.items()));
        } else if (route.page == Page.DETAIL && route.entry != null) {
            options.putString("FocusKey", focusKey(route.entry));
        }
        int index = browser ? route.index : 0;
        if (!options.equals(requestedOptions) || requestedPage != index) {
            requestedOptions = options.copy();
            requestedPage = index;
            ArmoryClientState.configure(options);
            ArmoryNetwork.request("refresh", -1, "", 0, index);
        }
    }

    private net.minecraft.nbt.ListTag previews(List<ItemStack> stacks) {
        net.minecraft.nbt.ListTag result = new net.minecraft.nbt.ListTag();
        stacks.forEach(stack -> result.add(stack.save(new CompoundTag())));
        return result;
    }

    private String focusKey(ArmoryEntry entry) {
        return entry.origin() == ArmoryEntry.Origin.CATALOGUE ? ArmoryNetwork.resourceKey(entry) : entry.key();
    }

    private void updateFocusedEntry() {
        if (route.page != Page.DETAIL || route.entry == null || ArmoryClientState.position() == null) {
            return;
        }
        CompoundTag focused = ArmoryClientState.snapshot().getCompound("Focused");
        if (!focused.getString("Key").equals(focusKey(route.entry))) {
            return;
        }
        ArmoryEntry old = route.entry;
        String id = focused.getString("Id");
        ItemStack stack = old.origin() == ArmoryEntry.Origin.INVENTORY && focused.contains("Item")
                ? ItemStack.of(focused.getCompound("Item")) : old.stack();
        route = new Route(route.page, new ArmoryEntry(old.key(), stack, new BigInteger(focused.getString("Quantity")),
                id.isEmpty() ? List.of() : List.of(id), old.origin(), old.recentOrder()), route.index);
    }

    private int fittedPageSize() {
        int height = (int) document.getViewportSize().height();
        int results = Math.max(100, height - 270);
        int rows = Math.max(1, (results + 4) / 104);
        int columns = switch (view) {
            case COMPACT -> 8;
            case LIST, TABLE -> 1;
            default -> 6;
        };
        int size = rows * columns;
        if (grouped) {
            size = Math.max(columns, size - columns);
        }
        return Math.min(60, size);
    }

    private void renderPage() {
        if (document == null) {
            return;
        }
        int fitted = fittedPageSize();
        if (pageSize != fitted) {
            pageSize = fitted;
            route = new Route(route.page, route.entry, 0);
        }
        requestQuery();
        updateFocusedEntry();
        Document.runWithContext(document, () -> {
            ContextMenu.closeActive();
            Element content = element("content");
            content.setInnerHTML("");
            element("page-title").setTextContent(pageTitle());
            updateNav();
            element("connection").setAttribute("class", ArmoryClientState.position() == null ? "badge-2 badge-2-yellow" : "badge-2 badge-2-green");
            element("connection").setTextContent(ArmoryClientState.position() == null ? "预览模式" : "已绑定军械库");
            CompoundTag snapshot = ArmoryClientState.snapshot();
            element("summary").setTextContent("配置 " + snapshot.getInt("EntryCount") + " · 总量 " + snapshot.getString("TotalQuantity"));
            message(snapshot.contains("Error") ? snapshot.getString("Error") : "左键查看详情，右键打开快捷菜单。");
            switch (route.page) {
                case HOME -> renderHome(content);
                case DETAIL -> renderDetails(content, route.entry);
                case IMPORT -> renderImport(content);
                default -> renderBrowser(content);
            }
        });
    }

    private List<ArmoryEntry> remoteEntries(String name) {
        List<ArmoryEntry> entries = new ArrayList<>();
        for (Tag value : ArmoryClientState.snapshot().getList(name, Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            ItemStack stack = ItemStack.of(tag.getCompound("Item"));
            String id = tag.getString("Id");
            if (!stack.isEmpty()) {
                entries.add(new ArmoryEntry(tag.getString("Key"), stack, new BigInteger(tag.getString("Quantity")),
                        id.isEmpty() ? List.of() : List.of(id), ArmoryEntry.Origin.valueOf(tag.getString("Origin")), tag.getInt("RecentOrder")));
            }
        }
        return List.copyOf(entries);
    }

    private List<ArmoryEntry> source(Page page) {
        if (ArmoryClientState.position() != null) {
            String remote = ArmoryClientState.snapshot().getString("Source");
            if (route.page == Page.HOME && remote.equals("HOME")) {
                return remoteEntries(switch (page) {
                    case FAVORITE -> "HomeFavorites";
                    case RECENT -> "HomeRecent";
                    case CATALOGUE -> "HomeCatalogue";
                    default -> "Entries";
                });
            }
            return remote.equals(page.name()) ? remoteEntries("Entries") : List.of();
        }
        if (page == Page.INVENTORY) {
            return List.of();
        }
        if (page == Page.FAVORITE) {
            return ArmoryEntries.remembered(Favorites.global().getFavorites(), ArmoryEntry.Origin.FAVORITE);
        }
        if (page == Page.RECENT) {
            return ArmoryEntries.remembered(RecentViews.items(), ArmoryEntry.Origin.RECENT);
        }
        if (catalogue.isEmpty()) {
            catalogue = ArmoryEntries.catalogue();
        }
        return catalogue;
    }











    private void openDetails(ArmoryEntry entry) {
        closeEquipmentDialog();
        history.push(route);
        RecentViews.record(entry.stack());
        previewStack = null;
        detailCodeDraft = "";
        route = new Route(Page.DETAIL, entry, 0);
        renderPage();
    }

    private boolean owns(ArmoryEntry entry) {
        return !entry.inventoryIds().isEmpty() && entry.origin() != ArmoryEntry.Origin.CATALOGUE;
    }

    private void contextMenu(ArmoryEntry entry, Position position) {
        List<ContextMenu.Item> items = new ArrayList<>();
        items.add(ContextMenu.Item.header(entry.name()));
        if (entry.kind() == ArmoryItemIdentity.Kind.GUN) {
            items.add(ContextMenu.Item.action("枪械详情 / 配件预览", () -> openDetails(entry)));
            items.add(ContextMenu.Item.action("复制改枪码", () -> copyCode(entry)));
            items.add(ContextMenu.Item.action("领取同型弹药", () -> supplyAmmo(entry)));
            items.add(ContextMenu.Item.action("制作同型弹药", () -> openCraftDialog(ammoEntry(entry), null)));
            if (owns(entry)) {
                ContextMenu.Item reload = ContextMenu.Item.action("消耗弹药补满弹匣", () -> submitEquipment("reload", entry, new CompoundTag()));
                items.add(equipmentPending ? reload.disabled() : reload);
            }
        }
        if (Favorites.global().supports(entry.stack())) {
            items.add(ContextMenu.Item.action(Favorites.global().containsFavorite(entry.stack()) ? "取消收藏" : "收藏配置", () -> toggleFavorite(entry)));
        }
        if (entry.kind() != ArmoryItemIdentity.Kind.OTHER) {
            items.add(ContextMenu.Item.action(entry.kind() == ArmoryItemIdentity.Kind.GUN ? "添置此枪械配置…" : "制作此军备…", () -> openCraftDialog(entry, null)));
        }
        if (owns(entry)) {
            items.add(ContextMenu.Item.action("取出一件", () -> withdraw(entry, 1)));
            items.add(ContextMenu.Item.action("取出一组", () -> withdraw(entry, entry.stack().getMaxStackSize())));
        }
        if (entry.origin() == ArmoryEntry.Origin.RECENT) {
            items.add(ContextMenu.Item.action("移出最近浏览", () -> {
                RecentViews.remove(entry.stack());
                renderPage();
            }));
        }
        ContextMenu.show(document, position, items, new ContextMenu.Options("ua-context-menu",
                "background:var(--surface);color:var(--ink);border:2px solid var(--edge);", null));
    }



    private void attachmentEditor(Element parent, ArmoryEntry entry, ItemStack shown) {
        GunEntry model = new GunEntry(0, shown);
        IGun gun = IGun.getIGunOrNull(shown);
        List<AttachmentType> slots = new ArrayList<>(model.getSupportedSlotTypes());
        if (slots.isEmpty()) {
            node(parent, "p", "form-help", "该枪械没有可改装槽位。");
            return;
        }
        attachmentSlot = Math.min(attachmentSlot, slots.size() - 1);
        Element slotTabs = node(parent, "div", "ua-slot-tabs", "");
        for (int i = 0; i < slots.size(); i++) {
            int index = i;
            Element control = button(slotTabs, slots.get(i).name(), () -> {
                attachmentSlot = index;
                renderPage();
            });
            if (i == attachmentSlot) control.setAttribute("class", "button active");
        }
        for (AttachmentType type : List.of(slots.get(attachmentSlot))) {
            Element panel = node(parent, "div", "ua-attachment-slot", "");
            GunEntry.SlotInfo slot = model.getSlot(type);
            node(panel, "strong", "", type.name() + " · " + (slot.getEffectiveId() == null ? "空槽位" : slot.getEffectiveId()));
            List<ItemStack> compatible = AttachmentItem.fillItemCategory(type).stream()
                    .filter(stack -> AttachmentUtils.canAttach(shown, stack))
                    .sorted(java.util.Comparator.comparing(stack -> stack.getHoverName().getString())).toList();
            node(panel, "div", "ua-anno", "兼容配件 · " + compatible.size() + " 件");
            Element list = node(panel, "div", "ua-attach-list", "");
            Set<String> seen = new LinkedHashSet<>();
            int shownCount = 0;
            for (ItemStack candidate : compatible) {
                String id = IAttachment.getIAttachmentOrNull(candidate).getAttachmentId(candidate).toString();
                if (!seen.add(id)) {
                    continue;
                }
                if (shownCount++ >= 6) {
                    node(panel, "p", "form-help", "仅显示前 6 项，先选择槽位再挑配件。");
                    break;
                }
                Element row = node(list, "div", "ua-attach-row", "");
                Element icon = node(row, "div", "ua-slot-icon", "");
                ItemStack iconStack = candidate.copy();
                iconStack.setCount(1);
                Element iconPreview = preview(icon, iconStack);
                iconPreview.setAttribute("class", "ua-slot-icon-item");
                Element main = node(row, "div", "ua-attach-main", "");
                node(main, "span", "ua-slot-name",
                        attachmentName(net.minecraft.resources.ResourceLocation.tryParse(id)));
                node(main, "span", "ua-slot-slot", id);
                Element buttons = node(row, "div", "ua-attach-actions", "");
                ItemStack fixed = candidate.copy();
                fixed.setCount(1);
                button(buttons, "预览", () -> {
                    ItemStack copy = shown.copy();
                    if (!AttachmentUtils.installAttachmentLikeRefit(copy, fixed, null)) {
                        throw new IllegalArgumentException("无法预览安装该配件");
                    }
                    previewStack = copy;
                    renderPage();
                }).setDisabled(gun.hasAttachmentLock(shown));
                button(buttons, "安装", () -> {
                    CompoundTag operation = new CompoundTag();
                    operation.putString("Attachment", id);
                    submitEquipment("install", entry, operation);
                }).setDisabled(!owns(entry) || entry.attachmentLocked() || equipmentPending);
            }
            Element actions = node(panel, "div", "ua-actions", "");
            button(actions, "预览拆下", () -> {
                ItemStack copy = shown.copy();
                IGun.getIGunOrNull(copy).unloadAttachment(copy, type);
                previewStack = copy;
                renderPage();
            }).setDisabled(gun.getAttachment(shown, type).isEmpty() || gun.hasAttachmentLock(shown));
            button(actions, "拆卸到库存", () -> {
                CompoundTag operation = new CompoundTag();
                operation.putString("Slot", type.name());
                submitEquipment("uninstall", entry, operation);
            }).setDisabled(!owns(entry) || entry.attachmentLocked() || equipmentPending
                    || IGun.getIGunOrNull(entry.stack()).getAttachment(entry.stack(), type).isEmpty());
        }
    }

    private void previewCode(ArmoryEntry entry, String text) {
        if (text.isBlank() || text.length() > 65536) {
            throw new IllegalArgumentException("无效的改枪码长度");
        }
        ItemStack stack = GunTemplateCodecs.share().createPreviewStack(GunTemplateCodecs.share().importCode(text.strip()));
        if (!java.util.Objects.equals(ArmoryItemIdentity.resourceId(stack), entry.resourceId())) {
            throw new IllegalArgumentException("改枪码型号与当前枪械不同，请从改枪码导入页预览其他枪械");
        }
        previewStack = stack;
        detailCodeDraft = text;
        renderPage();
    }

    private void openCraftDialog(ArmoryEntry entry, ItemStack blueprint) {
        if (entry == null) {
            message("该枪械未提供有效的弹药数据。");
            return;
        }
        closeEquipmentDialog();
        equipmentDialog = DialogWindow.open(document, DialogWindow.Options.of("添置 / 制作 · " + entry.name(), 620, 460, true), null);
        equipmentDialog.window().setAttribute("class", equipmentDialog.window().getAttribute("class") + " ua-equipment-dialog");
        Element body = equipmentDialog.content();
        body.setAttribute("style", "padding:10px;overflow:hidden;min-height:0;");
        List<Recipe<?>> recipes = ArmoryRecipeQueries.forItem(minecraft == null ? null : minecraft.level, entry.stack());
        node(body, "p", "form-help", "产物存入当前军械库，材料优先从军械库取用，不足时使用背包材料。配方材料由服务端核验。");
        if (recipes.isEmpty()) {
            node(body, "p", "alert alert-warning", "当前没有可用的军备制作配方；不会凭空生成物品。");
            return;
        }
        Element select = node(body, "select", "form-select", "");
        for (Recipe<?> recipe : recipes) {
            ItemStack output = recipe.getResultItem(minecraft.level.registryAccess());
            option(select, recipe.getId().toString(), recipe.getId() + " → " + output.getCount() + " 件 / 批");
        }
        select.setValue(recipes.get(0).getId().toString());
        Element description = node(body, "div", "ua-recipe-materials", "");
        Element materialControls = node(body, "div", "ua-actions", "");
        Element materialLabel = node(materialControls, "span", "ua-meta", "");
        Runnable showMaterials = () -> {
            description.setInnerHTML("");
            List<Ingredient> ingredients = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            Recipe<?> selected = recipes.stream().filter(recipe -> recipe.getId().toString().equals(select.getValue())).findFirst().orElse(recipes.get(0));
            if (selected instanceof GunSmithTableRecipe gunRecipe) {
                gunRecipe.getInputs().forEach(input -> {
                    ingredients.add(input.getIngredient());
                    counts.add(input.getCount());
                });
            } else {
                selected.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).forEach(ingredient -> {
                    ingredients.add(ingredient);
                    counts.add(1);
                });
            }
            int pages = Math.max(1, (ingredients.size() + 5) / 6);
            materialPage = Math.floorMod(materialPage, pages);
            materialLabel.setTextContent("材料 " + (materialPage + 1) + "/" + pages);
            for (int i = materialPage * 6; i < Math.min(ingredients.size(), materialPage * 6 + 6); i++) {
                ingredientDescription(description, ingredients.get(i), counts.get(i));
            }
        };
        button(materialControls, "上一组材料", () -> { materialPage--; showMaterials.run(); });
        button(materialControls, "下一组材料", () -> { materialPage++; showMaterials.run(); });
        select.addEventListener("change", event -> { materialPage = 0; showMaterials.run(); });
        materialPage = 0;
        showMaterials.run();
        Element row = node(body, "div", "ua-form-row", "");
        node(row, "label", "form-label", "制作批数");
        Element batches = node(row, "input", "form-input", "");
        batches.setAttribute("type", "number");
        batches.setAttribute("min", "1");
        batches.setAttribute("max", "64");
        batches.setValue("1");
        ItemStack configured = blueprint == null ? entry.stack() : blueprint.copy();
        boolean includeCode = entry.kind() == ArmoryItemIdentity.Kind.GUN
                && (blueprint != null || entry.origin() != ArmoryEntry.Origin.CATALOGUE);
        if (includeCode) {
            node(body, "p", "form-help", "将保留当前配件配置；额外配件也需要真实材料，替换下的配件退回库存。");
        }
        button(body, "确认制作并存入", () -> {
            int amount = Integer.parseInt(batches.getValue().strip());
            if (amount < 1 || amount > 64) {
                throw new IllegalArgumentException("单次批数必须为 1 至 64，可重复制作");
            }
            CompoundTag operation = new CompoundTag();
            operation.putString("Recipe", select.getValue());
            operation.putInt("Batches", amount);
            if (includeCode) {
                operation.putString("Code", GunTemplateCodecs.share().exportCode(configured, entry.name()));
            }
            submitEquipment("craft", entry, operation);
        }).setDisabled(ArmoryClientState.position() == null || equipmentPending);
    }

    private void ingredientDescription(Element parent, Ingredient ingredient, int count) {
        String choices = Arrays.stream(ingredient.getItems()).map(stack -> stack.getHoverName().getString())
                .distinct().limit(6).collect(Collectors.joining(" / "));
        node(parent, "p", "ua-meta", (choices.isEmpty() ? "未解析材料" : choices) + " ×" + count + " / 批");
    }

    private void option(Element select, String value, String label) {
        node(select, "option", "", label).setAttribute("value", value);
    }

    private ArmoryEntry ammoEntry(ArmoryEntry gun) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(gun.ammoId());
        if (id == null) {
            return null;
        }
        ItemStack stack = AmmoItemBuilder.create().setId(id).setCount(1).build();
        return new ArmoryEntry(ArmoryItemIdentity.key(stack), stack, BigInteger.ZERO, List.of(), ArmoryEntry.Origin.CATALOGUE, -1);
    }

    private void supplyAmmo(ArmoryEntry entry) {
        if (entry.ammoId().isBlank()) {
            message("未找到弹药类型。");
            return;
        }
        CompoundTag operation = new CompoundTag();
        operation.putString("Ammo", entry.ammoId());
        operation.putInt("Amount", 64);
        submitEquipment("supply-ammo", entry, operation);
    }

    private void submitEquipment(String action, ArmoryEntry entry, CompoundTag operation) {
        if (equipmentPending || ArmoryClientState.position() == null) {
            message(equipmentPending ? "正在等待上一次操作完成。" : "请先右键绑定一个军械库。");
            return;
        }
        if (!action.equals("craft") && !action.equals("supply-ammo") && !owns(entry)) {
            message("该配置不在当前军械库中，请先添置或存入。");
            return;
        }
        closeEquipmentDialog();
        equipmentPending = true;
        ArmoryNetwork.operate(action, entry.inventoryIds().isEmpty() ? "" : entry.inventoryIds().get(0), operation, route.index);
        renderPage();
        message("正在等待服务端核验物品和材料…");
    }

    private void closeEquipmentDialog() {
        if (equipmentDialog != null) {
            equipmentDialog.close();
            equipmentDialog = null;
        }
    }



    private void playerInventory(Element content) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
            if (!minecraft.player.getInventory().getItem(slot).isEmpty()) slots.add(slot);
        }
        int pages = Math.max(1, (slots.size() + 3) / 4);
        depositPage = Math.min(depositPage, pages - 1);
        Element panel = node(content, "section", "panel ua-inventory-player", "");
        Element header = node(panel, "div", "panel-header", "玩家背包 · 存入 " + (depositPage + 1) + "/" + pages);
        button(header, "上一组", () -> {
            depositPage--;
            renderPage();
        }).setDisabled(depositPage == 0);
        button(header, "下一组", () -> {
            depositPage++;
            renderPage();
        }).setDisabled(depositPage + 1 >= pages);
        Element body = node(panel, "div", "panel-body ua-actions", "");
        for (int index : slots.stream().skip(depositPage * 4L).limit(4).toList()) {
            ItemStack stack = minecraft.player.getInventory().getItem(index);
            button(body, stack.getHoverName().getString() + " ×" + stack.getCount(),
                    () -> ArmoryNetwork.request("deposit", index, "", Integer.MAX_VALUE, route.index));
        }
    }

    private void withdraw(ArmoryEntry entry, int count) {
        if (ArmoryClientState.position() == null || entry.inventoryIds().isEmpty()) {
            message("当前未绑定军械库或库存不足。");
            return;
        }
        ArmoryNetwork.request("withdraw", -1, entry.inventoryIds().get(0), count, route.index);
    }

    private void copyCode(ArmoryEntry entry) {
        Minecraft.getInstance().keyboardHandler.setClipboard(GunTemplateCodecs.share().exportCode(entry.stack(), entry.name()));
        message("改枪码已复制到剪贴板。");
    }

    private void toggleFavorite(ArmoryEntry entry) {
        if (Favorites.global().containsFavorite(entry.stack())) {
            Favorites.global().removeFavorite(entry.stack());
        } else {
            Favorites.global().addFavorite(entry.stack());
        }
        renderPage();
    }

    private Element preview(Element parent, ItemStack stack) {
        Element preview;
        if (ArmoryItemIdentity.kind(stack) == ArmoryItemIdentity.Kind.GUN) {
            preview = new GunShowElement(document);
            preview.setAttribute("item", String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
            preview.setAttribute("nbt", stack.getOrCreateTag().toString());
        } else {
            preview = new com.sighs.apricityui.element.Item(document);
            preview.setTextContent(stack.save(new CompoundTag()).toString());
        }
        preview.setAttribute("class", "ua-preview-item");
        parent.append(preview);
        return preview;
    }

    private Element node(Element parent, String tag, String css, String text) {
        Element element = Element.init(document.createElement(tag));
        if (!css.isEmpty()) {
            element.setAttribute("class", css);
        }
        if (!text.isEmpty()) {
            element.setTextContent(text);
        }
        parent.append(element);
        return element;
    }

    private Element button(Element parent, String text, Runnable action) {
        Element button = node(parent, "button", "button button-normal button-small", text);
        button.addEventListener("click", event -> {
            if (!button.isDisabled()) {
                try {
                    action.run();
                } catch (IllegalArgumentException exception) {
                    message(exception.getMessage());
                }
            }
        });
        return button;
    }

    private void action(String id, Runnable action) {
        element(id).addEventListener("click", event -> action.run());
    }

    private Element element(String id) {
        return document.getElementById(id);
    }

    private String value(String id) {
        return element(id).getValue().strip();
    }

    private Set<String> values(String id) {
        return Arrays.stream(value(id).replace('，', ',').split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private BigInteger integer(String id, BigInteger fallback) {
        return value(id).isEmpty() ? fallback : new BigInteger(value(id));
    }

    private ArmoryQuery.Range range(String id) {
        double minimum = value(id + "-min").isEmpty() ? Double.NEGATIVE_INFINITY : Double.parseDouble(value(id + "-min"));
        double maximum = value(id + "-max").isEmpty() ? Double.POSITIVE_INFINITY : Double.parseDouble(value(id + "-max"));
        return new ArmoryQuery.Range(minimum, maximum);
    }



    private void message(String text) {
        element("message").setTextContent(text);
    }

    // ------------------------------------------------------------------
    // Page rendering
    // ------------------------------------------------------------------

    private String pageTitle() {
        return switch (route.page) {
            case HOME -> "主页";
            case INVENTORY -> "库存管理";
            case CATALOGUE -> "军备总览 / 图鉴";
            case FAVORITE -> "军备总览 / 收藏夹";
            case RECENT -> "军备总览 / 最近浏览";
            case IMPORT -> "军备总览 / 改枪码导入";
            case DETAIL -> route.entry == null ? "枪械详情" : route.entry.name();
        };
    }

    private void updateNav() {
        boolean home = route.page == Page.HOME;
        boolean inventory = route.page == Page.INVENTORY;
        boolean catalogue = route.page == Page.CATALOGUE || route.page == Page.FAVORITE
                || route.page == Page.RECENT || route.page == Page.IMPORT;
        if (route.page == Page.DETAIL && route.entry != null) {
            inventory = route.entry.origin() == ArmoryEntry.Origin.INVENTORY;
            catalogue = !inventory;
        }
        setNav("nav-home", home);
        setNav("nav-inventory", inventory);
        setNav("nav-catalogue", catalogue);
    }

    private void setNav(String id, boolean active) {
        Element nav = element(id);
        if (nav != null) {
            nav.setAttribute("class", active ? "tab active" : "tab");
        }
    }

    private Element panel(Element parent, String extra) {
        return node(parent, "section", "panel " + extra, "");
    }

    private void panelHead(Element panel, String title, String actionLabel, Runnable action) {
        Element head = node(panel, "div", "panel-header", "");
        node(head, "span", "ua-panel-title", title);
        if (actionLabel != null) {
            button(head, actionLabel, action);
        }
    }

    private Element panelBody(Element panel) {
        return node(panel, "div", "panel-body", "");
    }

    private void statTile(Element parent, String value, String label) {
        Element tile = node(parent, "div", "ua-stat", "");
        node(tile, "span", "ua-stat-value", value);
        node(tile, "span", "ua-stat-label", label);
    }

    private void emptyState(Element parent, String title, String text) {
        Element box = node(parent, "div", "ua-empty", "");
        node(box, "strong", "", title);
        node(box, "span", "", text);
    }

    private String typeLabel(ArmoryEntry entry) {
        if (entry.kind() == ArmoryItemIdentity.Kind.AMMO) {
            String ammo = entry.ammoId().isBlank() ? String.valueOf(entry.resourceId()) : entry.ammoId();
            return ammo.isBlank() ? "弹药" : ammo;
        }
        return typeLabel(entry.type());
    }

    private String typeLabel(String type) {
        if (type == null || type.isBlank()) {
            return "未知";
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "rifle" -> "突击步枪";
            case "smg" -> "冲锋枪";
            case "sniper" -> "狙击步枪";
            case "shotgun" -> "霰弹枪";
            case "mg" -> "机枪";
            case "pistol" -> "手枪";
            case "rpg" -> "发射器";
            case "muzzle" -> "枪口";
            case "barrel" -> "枪管";
            case "grip" -> "握把";
            case "scope" -> "瞄具";
            case "stock" -> "枪托";
            case "extended_mag" -> "弹匣";
            case "laser" -> "激光";
            default -> type;
        };
    }

    private String shortCode(ArmoryEntry entry) {
        int hash = entry.key().hashCode();
        return String.format("#%04X", (hash ^ (hash >>> 16)) & 0xFFFF);
    }

    private String originLabel(ArmoryEntry entry) {
        return switch (entry.origin()) {
            case INVENTORY -> "库存";
            case CATALOGUE -> "图鉴";
            case FAVORITE -> "收藏";
            case RECENT -> "最近浏览";
            case IMPORT -> "改枪码预览";
        };
    }

    private void resetPage() {
        collapsedGroups.clear();
        resultsScroll = 0;
        route = new Route(route.page, route.entry, 0);
        renderPage();
    }

    private ArmoryQuery.Filter withFilter(String search, Set<ArmoryItemIdentity.Kind> kinds, Set<String> types,
                                          ArmoryQuery.Ownership ownership, boolean favoriteOnly) {
        return withFilter(search, kinds, types, filter.ammoIds(), ownership, favoriteOnly);
    }

    private ArmoryQuery.Filter withFilter(String search, Set<ArmoryItemIdentity.Kind> kinds, Set<String> types,
                                          Set<String> ammoIds, ArmoryQuery.Ownership ownership, boolean favoriteOnly) {
        return new ArmoryQuery.Filter(search, kinds, types, filter.namespaces(), ammoIds,
                ownership, filter.attachments(), filter.locked(), favoriteOnly,
                filter.minimumQuantity(), filter.maximumQuantity(), filter.damage(), filter.rpm(), filter.weight());
    }

    private void switchTab(Page page) {
        closeEquipmentDialog();
        previewStack = null;
        resultsScroll = 0;
        selectMode = false;
        selectedKeys.clear();
        route = new Route(page, null, 0);
        renderPage();
    }

    // ------------------------------------------------------------------
    // Home
    // ------------------------------------------------------------------

    private void renderHome(Element content) {
        Element home = node(content, "div", "ua-home", "");
        List<ArmoryEntry> inventory = source(Page.HOME);

        Element hero = panel(home, "ua-home-hero");
        panelHead(hero, "库存 · 概览", "进入库存 >", () -> navigate(Page.INVENTORY));
        Element heroBody = panelBody(hero);
        ArmoryEntry featured = inventory.stream()
                .filter(entry -> entry.kind() == ArmoryItemIdentity.Kind.GUN)
                .findFirst().orElse(inventory.isEmpty() ? null : inventory.get(0));
        if (featured == null) {
            emptyState(heroBody, "库存空空如也", "去图鉴添置第一把枪");
        } else {
            Element preview = preview(heroBody, featured.stack());
            preview.setAttribute("class", "ua-hero-preview");
            Element head = node(heroBody, "div", "ua-hero-head", "");
            Element info = node(head, "div", "", "");
            node(info, "div", "ua-hero-name", featured.name());
            node(info, "div", "ua-hero-sub", typeLabel(featured) + " · " + shortCode(featured)
                    + " · 配件 " + featured.attachmentCount());
            node(head, "span", "ua-anno", "库存 " + featured.quantity());
            Element stats = node(heroBody, "div", "ua-hero-stats", "");
            statTile(stats, String.valueOf(inventory.stream()
                    .filter(entry -> entry.kind() == ArmoryItemIdentity.Kind.GUN).count()), "枪械");
            statTile(stats, String.valueOf(inventory.stream()
                    .filter(entry -> entry.kind() == ArmoryItemIdentity.Kind.AMMO).count()), "弹药");
            statTile(stats, String.valueOf(inventory.stream()
                    .filter(entry -> entry.kind() == ArmoryItemIdentity.Kind.ATTACHMENT).count()), "配件");
        }

        Element favouritePanel = panel(home, "ua-home-fav");
        panelHead(favouritePanel, "收藏夹 · 随机", "换一批", () -> {
            randomSeed = System.nanoTime();
            renderPage();
        });
        Element favouriteBody = panelBody(favouritePanel);
        List<ArmoryEntry> favourites = new ArrayList<>(source(Page.FAVORITE));
        Collections.shuffle(favourites, new Random(randomSeed));
        if (favourites.isEmpty()) {
            emptyState(favouriteBody, "还没有收藏", "在任意枪械上按 * 收藏");
        } else {
            Element grid = node(favouriteBody, "div", "ua-cards ua-cards-3", "");
            favourites.stream().limit(3).forEach(entry -> itemCard(grid, entry, true, false));
        }

        Element recentPanel = panel(home, "ua-home-recent");
        panelHead(recentPanel, "最近浏览", "查看全部", () -> navigate(Page.RECENT));
        Element recentBody = panelBody(recentPanel);
        List<ArmoryEntry> recents = source(Page.RECENT);
        if (recents.isEmpty()) {
            emptyState(recentBody, "暂无浏览记录", "浏览过的枪械会出现在这里");
        } else {
            Element list = node(recentBody, "div", "ua-recent", "");
            recents.stream().limit(6).forEach(entry -> recentRow(list, entry));
        }

        Element addPanel = panel(home, "ua-home-add");
        panelHead(addPanel, "添置 · 随机推荐", "打开军备总览 >", () -> navigate(Page.CATALOGUE));
        Element addBody = panelBody(addPanel);
        List<ArmoryEntry> suggestions = new ArrayList<>(source(Page.CATALOGUE));
        Collections.shuffle(suggestions, new Random(randomSeed + 1));
        if (suggestions.isEmpty()) {
            emptyState(addBody, "没有可添置的枪械", "图鉴已全部收集");
        } else {
            Element row = node(addBody, "div", "ua-add-row", "");
            suggestions.stream().limit(4).forEach(entry -> addCard(row, entry));
        }
    }

    private void itemCard(Element parent, ArmoryEntry entry, boolean favourite, boolean compact) {
        boolean selected = selectedKeys.contains(entry.key());
        Element card = node(parent, "div", "ua-card" + (selected ? " selected" : ""), "");
        card.setAttribute("tabindex", "0");
        Element preview = preview(card, entry.stack());
        preview.setAttribute("class", "ua-card-preview");
        node(card, "span", "ua-card-name", entry.name());
        node(card, "span", "ua-card-meta", typeLabel(entry) + " · " + shortCode(entry));
        Element foot = node(card, "div", "ua-card-foot", "");
        node(foot, "span", "", "x" + entry.quantity());
        node(foot, "span", favourite ? "ua-fav-mark" : "ua-anno",
                favourite ? "* 收藏" : entry.kind() == ArmoryItemIdentity.Kind.GUN
                        ? "配件 " + entry.attachmentCount() : typeLabel(entry));
        if (selected) {
            node(foot, "span", "ua-selected-mark", "V");
        }
        bindEntry(card, entry);
    }

    private void addCard(Element parent, ArmoryEntry entry) {
        Element card = node(parent, "div", "ua-add-card", "");
        card.setAttribute("tabindex", "0");
        Element preview = preview(card, entry.stack());
        preview.setAttribute("class", "ua-card-preview");
        Element info = node(card, "div", "ua-add-info", "");
        node(info, "span", "ua-card-name", entry.name());
        node(info, "span", "ua-card-meta", typeLabel(entry) + " · " + originLabel(entry));
        bindEntry(card, entry);
    }

    private void recentRow(Element parent, ArmoryEntry entry) {
        Element row = node(parent, "div", "ua-recent-row", "");
        row.setAttribute("tabindex", "0");
        node(row, "span", "ua-recent-name", entry.name());
        node(row, "span", "ua-recent-code", shortCode(entry));
        node(row, "span", "ua-recent-when", entry.recentOrder() >= 0 ? "#" + entry.recentOrder() : "刚刚");
        bindEntry(row, entry);
    }

    private void bindEntry(Element element, ArmoryEntry entry) {
        element.addEventListener("click", event -> {
            if (selectMode) {
                if (!selectedKeys.remove(entry.key())) {
                    selectedKeys.add(entry.key());
                }
                renderPage();
            } else {
                openDetails(entry);
            }
        });
        element.addEventListener("contextmenu", event -> {
            event.preventDefault();
            event.stopPropagation();
            Position position = event instanceof MouseEvent mouse
                    ? new Position(mouse.clientX, mouse.clientY) : Position.ZERO;
            contextMenu(entry, position);
        });
    }

    // ------------------------------------------------------------------
    // Inventory / catalogue
    // ------------------------------------------------------------------

    private void renderBrowser(Element content) {
        Element browser = node(content, "div", "ua-browser", "");
        renderRail(browser);
        Element main = node(browser, "div", "ua-browse", "");
        if (route.page != Page.INVENTORY) {
            renderSubtabs(main);
        }
        renderToolbar(main);
        Element results = node(main, "div", "ua-results", "");
        Element resultsInner = node(results, "div", "ua-results-inner", "");
        resultsInner.setAttribute("style", "transform:translateY(-" + resultsScroll + "px)");
        List<ArmoryEntry> entries;
        int current;
        int pages;
        int total;
        if (ArmoryClientState.position() != null) {
            CompoundTag snapshot = ArmoryClientState.snapshot();
            entries = source(route.page);
            current = snapshot.getInt("Page");
            pages = Math.max(1, snapshot.getInt("PageCount"));
            total = snapshot.getInt("MatchCount");
            if (snapshot.getString("Source").equals(route.page.name())
                    && snapshot.getInt("RequestedPage") == route.index) {
                route = new Route(route.page, route.entry, current);
            }
        } else {
            Set<String> favourites = Favorites.global().getFavorites().stream()
                    .map(ArmoryItemIdentity::key).collect(Collectors.toSet());
            ArmoryQuery.Page page = ArmoryQuery.select(source(route.page), filter, favourites,
                    route.page == Page.RECENT && sort == ArmoryQuery.Sort.NAME ? ArmoryQuery.Sort.RECENT : sort,
                    descending, route.index, pageSize);
            entries = page.entries();
            current = page.page();
            pages = page.pageCount();
            total = page.total();
            route = new Route(route.page, route.entry, current);
        }
        renderEntries(resultsInner, entries);
        renderPager(main, current, pages, total);
    }

    private void renderRail(Element parent) {
        Element rail = panel(parent, "ua-rail");
        Element head = node(rail, "div", "panel-header", "");
        node(head, "span", "ua-panel-title", "筛选");
        Element body = panelBody(rail);
        Element title = node(body, "div", "ua-rail-head", "");
        node(title, "strong", "", route.page == Page.INVENTORY ? "库存" : "图鉴");
        node(title, "span", "ua-anno", "当前页 " + source(route.page).size() + " 件");

        List<ArmoryEntry> all = source(route.page);
        Element tree = node(body, "div", "ua-tree", "");
        treeItem(tree, "全部", String.valueOf(all.size()), filter.kinds().isEmpty(), () -> {
            filter = withFilter(filter.search(), Set.of(), Set.of(), filter.ownership(), filter.favoriteOnly());
            resetPage();
        });
        for (ArmoryItemIdentity.Kind kind : List.of(ArmoryItemIdentity.Kind.GUN,
                ArmoryItemIdentity.Kind.AMMO, ArmoryItemIdentity.Kind.ATTACHMENT,
                ArmoryItemIdentity.Kind.OTHER)) {
            long count = all.stream().filter(entry -> entry.kind() == kind).count();
            if (count == 0 && kind == ArmoryItemIdentity.Kind.OTHER) {
                continue;
            }
            boolean active = filter.kinds().size() == 1 && filter.kinds().contains(kind);
            treeItem(tree, kindLabel(kind), String.valueOf(count), active, () -> {
                filter = withFilter(filter.search(), Set.of(kind), Set.of(), filter.ownership(), filter.favoriteOnly());
                resetPage();
            });
            if (active) {
                Map<String, Long> types = new LinkedHashMap<>();
                for (ArmoryEntry entry : all) {
                    if (entry.kind() != kind) {
                        continue;
                    }
                    String raw = kind == ArmoryItemIdentity.Kind.AMMO ? entry.ammoId() : entry.type();
                    if (raw == null) {
                        raw = "";
                    }
                    types.merge(raw, 1L, Long::sum);
                }
                boolean ammoGroup = kind == ArmoryItemIdentity.Kind.AMMO;
                types.forEach((raw, typeCount) -> {
                    String label = ammoGroup ? (raw.isBlank() ? "未知口径" : raw)
                            : (raw.isBlank() ? "未知" : typeLabel(raw));
                    boolean typeActive = ammoGroup
                            ? filter.ammoIds().size() == 1 && filter.ammoIds().contains(raw)
                            : filter.types().size() == 1 && filter.types().contains(raw);
                    String key = raw;
                    treeItem(tree, label, String.valueOf(typeCount), typeActive, () -> {
                        filter = ammoGroup
                                ? withFilter(filter.search(), Set.of(kind), Set.of(),
                                key.isBlank() ? Set.of() : Set.of(key), filter.ownership(), filter.favoriteOnly())
                                : withFilter(filter.search(), Set.of(kind),
                                key.isBlank() ? Set.of() : Set.of(key), filter.ownership(), filter.favoriteOnly());
                        resetPage();
                    }, true);
                });
            }
        }
        node(tree, "div", "ua-tree-sep", "");
        treeItem(tree, filter.favoriteOnly() ? "仅收藏 · 开" : "仅收藏", "", filter.favoriteOnly(), () -> {
            filter = withFilter(filter.search(), filter.kinds(), filter.types(),
                    filter.ownership(), !filter.favoriteOnly());
            resetPage();
        });
        treeItem(tree, "最近浏览", "", false, () -> navigate(Page.RECENT));
    }

    private void treeItem(Element parent, String label, String count, boolean active, Runnable action) {
        treeItem(parent, label, count, active, action, false);
    }

    private void treeItem(Element parent, String label, String count, boolean active, Runnable action, boolean child) {
        Element item = node(parent, "div", "ua-tree-item" + (child ? " ua-tree-child" : "")
                + (active ? " active" : ""), "");
        item.setAttribute("tabindex", "0");
        node(item, "span", "", label);
        if (!count.isEmpty()) {
            node(item, "span", "ua-tree-count", count);
        }
        item.addEventListener("click", event -> action.run());
    }

    private String kindLabel(ArmoryItemIdentity.Kind kind) {
        return switch (kind) {
            case GUN -> "枪械";
            case AMMO -> "弹药";
            case ATTACHMENT -> "零散配件";
            case OTHER -> "其他";
        };
    }

    private void renderSubtabs(Element parent) {
        Element tabs = node(parent, "div", "ua-subtabs", "");
        subtab(tabs, "图鉴", Page.CATALOGUE);
        subtab(tabs, "收藏夹", Page.FAVORITE);
        subtab(tabs, "最近浏览", Page.RECENT);
        subtab(tabs, "改枪码导入", Page.IMPORT);
    }

    private void subtab(Element parent, String label, Page page) {
        Element tab = node(parent, "button", "tab" + (route.page == page ? " active" : ""), label);
        tab.setAttribute("type", "button");
        tab.addEventListener("click", event -> switchTab(page));
    }

    private void renderToolbar(Element parent) {
        Element bar = node(parent, "div", "ua-browse-toolbar", "");
        if (selectMode) {
            renderBatchBar(bar);
            return;
        }

        Element row1 = node(bar, "div", "ua-toolbar-row", "");
        Element search = node(row1, "input", "form-input ua-search", "");
        search.setAttribute("type", "text");
        search.setAttribute("placeholder", "搜索名称 / 改码 / 类型");
        search.setValue(filter.search());
        search.addEventListener("change", event -> {
            filter = withFilter(search.getValue(), filter.kinds(), filter.types(),
                    filter.ownership(), filter.favoriteOnly());
            resetPage();
        });

        Element ownership = node(row1, "select", "form-select", "");
        option(ownership, "ANY", "全部状态");
        option(ownership, "COLLECTED", "已收集");
        option(ownership, "MISSING", "未收集");
        ownership.setValue(filter.ownership().name());
        ownership.addEventListener("change", event -> {
            filter = withFilter(filter.search(), filter.kinds(), filter.types(),
                    ArmoryQuery.Ownership.valueOf(ownership.getValue()), filter.favoriteOnly());
            resetPage();
        });

        button(row1, filter.favoriteOnly() ? "仅收藏：开" : "仅收藏：关", () -> {
            filter = withFilter(filter.search(), filter.kinds(), filter.types(),
                    filter.ownership(), !filter.favoriteOnly());
            resetPage();
        });
        button(row1, "清除筛选", () -> {
            filter = ArmoryQuery.Filter.all();
            resetPage();
        });
        button(row1, filterOpen ? "收起高级筛选" : "高级筛选", () -> {
            filterOpen = !filterOpen;
            renderPage();
        });
        button(row1, "多选", () -> {
            selectMode = true;
            selectedKeys.clear();
            renderPage();
        });

        Element row2 = node(bar, "div", "ua-toolbar-row", "");
        node(row2, "span", "ua-toolbar-label", "视图");
        Element segmented = node(row2, "div", "ua-segmented", "");
        for (View mode : List.of(View.GRID, View.LIST, View.TABLE, View.COMPACT)) {
            Element seg = node(segmented, "button", "seg" + (view == mode ? " active" : ""), viewLabel(mode));
            seg.setAttribute("type", "button");
            seg.addEventListener("click", event -> {
                view = mode;
                renderPage();
            });
        }

        node(row2, "span", "ua-toolbar-label", "分组");
        Element toggle = node(row2, "span", "switch" + (grouped ? " on" : ""), "");
        toggle.setAttribute("tabindex", "0");
        toggle.addEventListener("click", event -> {
            grouped = !grouped;
            renderPage();
        });
        Element basis = node(row2, "select", "form-select", "");
        for (GroupBy by : GroupBy.values()) {
            option(basis, by.name(), by.label);
        }
        basis.setValue(groupBy.name());
        basis.setAttribute("style", grouped ? "width:130px" : "width:130px;opacity:.45");
        basis.addEventListener("change", event -> {
            groupBy = GroupBy.valueOf(basis.getValue());
            renderPage();
        });
        node(row2, "span", "ua-toolbar-label", grouped ? "分组开启" : "平铺全部");

        Element row3 = node(bar, "div", "ua-toolbar-row", "");
        node(row3, "span", "ua-toolbar-label", "排序");
        Element sortSelect = node(row3, "select", "form-select", "");
        for (ArmoryQuery.Sort value : ArmoryQuery.Sort.values()) {
            option(sortSelect, value.name(), sortLabel(value));
        }
        sortSelect.setValue(sort.name());
        sortSelect.addEventListener("change", event -> {
            sort = ArmoryQuery.Sort.valueOf(sortSelect.getValue());
            resetPage();
        });
        button(row3, descending ? "降序" : "升序", () -> {
            descending = !descending;
            resetPage();
        });

        if (filterOpen) {
            renderFilterPanel(bar);
        }
    }

    private void renderBatchBar(Element bar) {
        Element batch = node(bar, "div", "ua-batch-bar", "");
        node(batch, "span", "ua-batch-count", "已选 " + selectedKeys.size() + " 件");
        List<ArmoryEntry> entries = source(route.page);
        button(batch, "全选当前页", () -> {
            for (ArmoryEntry entry : entries) {
                selectedKeys.add(entry.key());
            }
            renderPage();
        });
        button(batch, "加入收藏", () -> {
            for (ArmoryEntry entry : entries) {
                if (selectedKeys.contains(entry.key()) && Favorites.global().supports(entry.stack())) {
                    Favorites.global().addFavorite(entry.stack());
                }
            }
            renderPage();
        });
        button(batch, "取消收藏", () -> {
            for (ArmoryEntry entry : entries) {
                if (selectedKeys.contains(entry.key()) && Favorites.global().supports(entry.stack())) {
                    Favorites.global().removeFavorite(entry.stack());
                }
            }
            renderPage();
        });
        button(batch, "移出库存", () -> {
            for (ArmoryEntry entry : entries) {
                if (selectedKeys.contains(entry.key()) && owns(entry)) {
                    withdraw(entry, 1);
                }
            }
            selectedKeys.clear();
            renderPage();
        });
        button(batch, "清空选择", () -> {
            selectedKeys.clear();
            renderPage();
        });
        button(batch, "退出多选", () -> {
            selectMode = false;
            selectedKeys.clear();
            renderPage();
        });
    }

    private void renderFilterPanel(Element bar) {
        Element panel = node(bar, "div", "ua-filter-panel", "");
        filterSelect(panel, "配件状态", "f-attachments",
                new String[]{"ANY", "BARE", "MODIFIED"},
                new String[]{"全部", "裸枪", "已改装"}, filter.attachments().name());
        filterSelect(panel, "锁定状态", "f-locked",
                new String[]{"ANY", "NO", "YES"},
                new String[]{"不限", "未锁定", "已锁定"}, filter.locked().name());
        filterInput(panel, "口径 ID", "f-ammo", String.join(",", filter.ammoIds()));
        filterInput(panel, "来源模组", "f-namespace", String.join(",", filter.namespaces()));
        filterRangeRow(panel, "数量", "f-q", quantityValue(filter.minimumQuantity()),
                filter.maximumQuantity() == null ? "" : filter.maximumQuantity().toString());
        filterRangeRow(panel, "伤害", "f-dmg", rangeValue(filter.damage().minimum()),
                rangeValue(filter.damage().maximum()));
        filterRangeRow(panel, "射速", "f-rpm", rangeValue(filter.rpm().minimum()),
                rangeValue(filter.rpm().maximum()));
        filterRangeRow(panel, "重量", "f-weight", rangeValue(filter.weight().minimum()),
                rangeValue(filter.weight().maximum()));
        Element actions = node(panel, "div", "ua-filter-actions", "");
        button(actions, "应用筛选", () -> {
            filter = new ArmoryQuery.Filter(filter.search(), filter.kinds(), filter.types(),
                    values("f-namespace"), values("f-ammo"), filter.ownership(),
                    ArmoryQuery.Attachments.valueOf(value("f-attachments")),
                    ArmoryQuery.Toggle.valueOf(value("f-locked")), filter.favoriteOnly(),
                    integer("f-q-min", BigInteger.ZERO), integerOrNull("f-q-max"),
                    range("f-dmg"), range("f-rpm"), range("f-weight"));
            filterOpen = false;
            resetPage();
        });
        button(actions, "重置", () -> {
            filter = ArmoryQuery.Filter.all();
            filterOpen = false;
            resetPage();
        });
    }

    private Element filterSelect(Element panel, String label, String id, String[] values, String[] labels, String current) {
        Element control = filterRow(panel, label);
        Element select = node(control, "select", "form-select", "");
        select.setAttribute("id", id);
        for (int i = 0; i < values.length; i++) {
            option(select, values[i], labels[i]);
        }
        select.setValue(current);
        return select;
    }

    private void filterInput(Element panel, String label, String id, String current) {
        Element control = filterRow(panel, label);
        Element input = node(control, "input", "form-input", "");
        input.setAttribute("type", "text");
        input.setAttribute("id", id);
        input.setValue(current == null ? "" : current);
    }

    private void filterRangeRow(Element panel, String label, String prefix, String minimum, String maximum) {
        Element control = filterRow(panel, label);
        Element low = node(control, "input", "form-input", "");
        low.setAttribute("type", "text");
        low.setAttribute("id", prefix + "-min");
        low.setValue(minimum);
        node(control, "span", "ua-filter-label", "-");
        Element high = node(control, "input", "form-input", "");
        high.setAttribute("type", "text");
        high.setAttribute("id", prefix + "-max");
        high.setValue(maximum);
    }

    private Element filterRow(Element panel, String label) {
        Element row = node(panel, "div", "ua-filter-row", "");
        node(row, "span", "ua-filter-label", label);
        return node(row, "div", "ua-filter-control", "");
    }

    private String quantityValue(BigInteger value) {
        return value == null || value.signum() == 0 ? "" : value.toString();
    }

    private String rangeValue(double value) {
        return Double.isInfinite(value) ? "" : format(value);
    }

    private BigInteger integerOrNull(String id) {
        String raw = value(id);
        return raw.isEmpty() ? null : new BigInteger(raw);
    }

    private String viewLabel(View mode) {
        return switch (mode) {
            case GRID, PREVIEW -> "网格";
            case LIST -> "列表";
            case TABLE -> "表格";
            case COMPACT -> "紧凑";
        };
    }

    private String sortLabel(ArmoryQuery.Sort value) {
        return switch (value) {
            case NAME -> "名称";
            case ID -> "资源 ID";
            case QUANTITY -> "库存数量";
            case ATTACHMENTS -> "配件数";
            case DAMAGE -> "伤害";
            case RPM -> "射速";
            case WEIGHT -> "重量";
            case RECENT -> "最近浏览";
        };
    }

    private void renderEntries(Element parent, List<ArmoryEntry> entries) {
        if (entries.isEmpty()) {
            if (filter.equals(ArmoryQuery.Filter.all())) {
                emptyState(parent, "这里还没有军备", "去图鉴添置，或从背包存入");
            } else {
                emptyState(parent, "没有匹配项", "调整筛选条件后重试");
            }
            return;
        }
        if (view == View.TABLE) {
            renderTable(parent, entries);
            return;
        }
        if (!grouped) {
            renderGrid(parent, entries);
            return;
        }
        Map<String, List<ArmoryEntry>> groups = new LinkedHashMap<>();
        for (ArmoryEntry entry : entries) {
            groups.computeIfAbsent(groupLabel(entry), key -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<String, List<ArmoryEntry>> group : groups.entrySet()) {
            Element section = node(parent, "section", "ua-group", "");
            Element head = node(section, "div", "ua-group-head", "");
            node(head, "span", "", group.getKey());
            node(head, "span", "ua-group-count", group.getValue().size() + " 件");
            node(head, "span", "ua-group-spacer", "");
            Element control = button(head, collapsedGroups.contains(group.getKey()) ? "展开" : "折叠", () -> {
                if (!collapsedGroups.remove(group.getKey())) {
                    collapsedGroups.add(group.getKey());
                }
                renderPage();
            });
            control.setAttribute("class", "ua-group-toggle");
            if (!collapsedGroups.contains(group.getKey())) {
                renderGrid(section, group.getValue());
            }
        }
    }

    private void renderGrid(Element parent, List<ArmoryEntry> entries) {
        if (view == View.LIST) {
            Element list = node(parent, "div", "ua-list", "");
            for (ArmoryEntry entry : entries) {
                listRow(list, entry);
            }
            return;
        }
        Element grid = node(parent, "div", view == View.COMPACT ? "ua-compact" : "ua-cards ua-browse-grid", "");
        for (ArmoryEntry entry : entries) {
            itemCard(grid, entry, false, view == View.COMPACT);
        }
    }

    private void listRow(Element parent, ArmoryEntry entry) {
        Element row = node(parent, "div", "ua-list-row"
                + (selectedKeys.contains(entry.key()) ? " selected" : ""), "");
        row.setAttribute("tabindex", "0");
        Element preview = preview(row, entry.stack());
        preview.setAttribute("class", "ua-card-preview");
        Element main = node(row, "div", "ua-list-main", "");
        node(main, "span", "ua-card-name", entry.name());
        node(main, "span", "ua-card-meta", typeLabel(entry) + " · " + shortCode(entry)
                + (entry.kind() == ArmoryItemIdentity.Kind.GUN ? " · 配件 " + entry.attachmentCount() : ""));
        Element stats = node(row, "div", "ua-list-stats", "");
        node(stats, "span", "", "x" + entry.quantity());
        node(stats, "span", "", originLabel(entry));
        node(stats, "span", "ua-anno", "详情 >");
        bindEntry(row, entry);
    }

    private void renderTable(Element parent, List<ArmoryEntry> entries) {
        Element wrap = node(parent, "div", "ua-table-wrap", "");
        Element table = node(wrap, "table", "table", "");
        Element head = node(table, "thead", "", "");
        Element headRow = node(head, "tr", "", "");
        for (String label : List.of("名称", "类型", "改码", "配件", "数量", "来源")) {
            node(headRow, "th", "", label);
        }
        Element body = node(table, "tbody", "", "");
        for (ArmoryEntry entry : entries) {
            Element row = node(body, "tr", selectedKeys.contains(entry.key()) ? "selected" : "", "");
            node(row, "td", "", entry.name());
            node(row, "td", "", typeLabel(entry));
            node(row, "td", "", shortCode(entry));
            node(row, "td", "", entry.kind() == ArmoryItemIdentity.Kind.GUN
                    ? String.valueOf(entry.attachmentCount()) : "--");
            node(row, "td", "", "x" + entry.quantity());
            node(row, "td", "", originLabel(entry));
            row.addEventListener("click", event -> {
                if (selectMode) {
                    if (!selectedKeys.remove(entry.key())) {
                        selectedKeys.add(entry.key());
                    }
                    renderPage();
                } else {
                    openDetails(entry);
                }
            });
            row.addEventListener("contextmenu", event -> {
                event.preventDefault();
                event.stopPropagation();
                Position position = event instanceof MouseEvent mouse
                        ? new Position(mouse.clientX, mouse.clientY) : Position.ZERO;
                contextMenu(entry, position);
            });
        }
    }

    private String groupLabel(ArmoryEntry entry) {
        return switch (groupBy) {
            case TYPE -> typeLabel(entry);
            case AMMO -> entry.ammoId().isBlank() ? "无口径" : entry.ammoId();
            case NAMESPACE -> entry.namespace().isBlank() ? "未知来源" : entry.namespace();
            case FAVORITE -> Favorites.global().containsFavorite(entry.stack()) ? "已收藏" : "未收藏";
            case ORIGIN -> originLabel(entry);
        };
    }

    private void renderPager(Element parent, int current, int pages, int total) {
        Element pager = node(parent, "div", "ua-pager", "");
        node(pager, "span", "", "第 " + (current + 1) + " / " + pages + " 页 · " + total + " 件匹配");
        Element controls = node(pager, "div", "ua-pager-actions", "");
        button(controls, "上移", () -> {
            resultsScroll = Math.max(0, resultsScroll - 220);
            renderPage();
        }).setDisabled(resultsScroll <= 0);
        button(controls, "下移", () -> {
            resultsScroll += 220;
            renderPage();
        });
        button(controls, "上一页", () -> changePage(-1)).setDisabled(current <= 0);
        button(controls, "下一页", () -> changePage(1)).setDisabled(current + 1 >= pages);
    }

    // ------------------------------------------------------------------
    // Detail
    // ------------------------------------------------------------------

    private void renderDetails(Element content, ArmoryEntry entry) {
        if (entry == null) {
            emptyState(content, "没有选中的枪械", "返回列表重新选择");
            return;
        }
        ArmoryEntry shown = previewStack == null ? entry : new ArmoryEntry(ArmoryItemIdentity.key(previewStack),
                previewStack, BigInteger.ZERO, List.of(), ArmoryEntry.Origin.IMPORT, -1);
        Element root = node(content, "div", "ua-detail", "");

        Element left = panel(root, "ua-detail-left");
        panelHead(left, "枪械", null, null);
        Element leftBody = panelBody(left);
        Element preview = preview(leftBody, shown.stack());
        preview.setAttribute("class", "ua-hero-preview");
        Element identity = node(leftBody, "div", "ua-identity", "");
        node(identity, "h2", "", entry.name());
        node(identity, "div", "ua-anno", shortCode(entry) + " · " + typeLabel(entry)
                + " · " + shown.attachmentCount() + " 件配件");
        Element tags = node(leftBody, "div", "ua-tags", "");
        node(tags, "span", "badge", originLabel(entry));
        if (Favorites.global().containsFavorite(shown.stack())) {
            node(tags, "span", "badge badge-warning", "* 已收藏");
        }

        Element actions = node(leftBody, "div", "ua-actions", "");
        button(actions, Favorites.global().containsFavorite(shown.stack()) ? "取消收藏" : "收藏当前配置",
                () -> toggleFavorite(shown));
        boolean isGun = shown.kind() == ArmoryItemIdentity.Kind.GUN;
        if (owns(entry)) {
            button(actions, previewStack == null ? "放入背包" : "放入背包（原枪械）",
                    () -> withdraw(entry, 1)).setDisabled(equipmentPending);
            if (isGun) {
                IGun gun = IGun.getIGunOrNull(entry.stack());
                button(actions, "补充弹药 / 补满弹匣",
                        () -> submitEquipment("reload", entry, new CompoundTag()))
                        .setDisabled(equipmentPending || gun == null || gun.useDummyAmmo(entry.stack())
                                || gun.useInventoryAmmo(entry.stack()));
            }
        }
        button(actions, isGun ? "添置同款配置" : "制作此军备", () -> openCraftDialog(entry, previewStack));
        if (isGun) {
            button(actions, "领取同型弹药", () -> supplyAmmo(entry))
                    .setDisabled(equipmentPending || ArmoryClientState.position() == null);
            button(actions, "复制改枪码", () -> copyCode(shown));
        }

        Element center = panel(root, "ua-detail-center");
        center.setAttribute("style", "width:" + Math.max(320,
                (int) document.getViewportSize().width() - 604) + "px");
        panelHead(center, isGun ? "性能数据" : "物品信息", detailTab == 1 ? "完成" : "改装", () -> {
            detailTab = detailTab == 1 ? 0 : 1;
            renderPage();
        });
        Element centerBody = panelBody(center);
        renderStats(centerBody, shown);
        if (isGun) {
            if (detailTab == 1) {
                node(centerBody, "div", "ua-tree-sep", "");
                attachmentEditor(centerBody, entry, shown.stack());
            } else {
                renderSlots(centerBody, entry, shown);
            }
        }

        Element right = panel(root, "ua-detail-right");
        panelHead(right, isGun ? "弹药与改枪码" : "物品详情", null, null);
        Element rightBody = panelBody(right);
        if (isGun) {
            renderAmmo(rightBody, entry, shown);
            renderCode(rightBody, entry, shown);
        } else {
            Element kv = node(rightBody, "div", "ua-kv", "");
            node(kv, "span", "", "数量");
            node(kv, "span", "", "x" + shown.quantity());
            Element kv2 = node(rightBody, "div", "ua-kv", "");
            node(kv2, "span", "", "来源");
            node(kv2, "span", "", originLabel(entry));
        }
    }

    private void renderStats(Element parent, ArmoryEntry shown) {
        GunData data = shown.gunData();
        if (data == null) {
            node(parent, "p", "ua-muted", "该物品没有枪械数据。");
            return;
        }
        double damage = data.getBulletData() == null ? 0 : data.getBulletData().getDamageAmount();
        double damageWith = com.tacz.guns.util.AttachmentDataUtils.getDamageWithAttachment(shown.stack(), data);
        int rpm = data.getRoundsPerMinute();
        int magazine = com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(shown.stack(), data);
        float weight = data.getWeight();
        double weightWith = com.tacz.guns.util.AttachmentDataUtils.getWightWithAttachment(shown.stack(), data);
        Element bars = node(parent, "div", "ua-statbars", "");
        statBar(bars, "伤害", format(damageWith), damageWith / 100.0, delta(damageWith - damage));
        statBar(bars, "射速", String.valueOf(rpm), rpm / 1200.0, "");
        statBar(bars, "弹匣", String.valueOf(magazine), magazine / 60.0, "");
        statBar(bars, "重量", format(weightWith), weightWith / 15.0, delta(weightWith - weight));
        statBar(bars, "瞄准", String.valueOf(data.getAimTime()), Math.min(1, data.getAimTime() / 2.0), "");
        Element kv = node(parent, "div", "ua-kv", "");
        node(kv, "span", "", "口径");
        node(kv, "span", "", shown.ammoId().isBlank() ? "--" : shown.ammoId());
    }

    private void statBar(Element parent, String label, String value, double ratio, String delta) {
        Element row = node(parent, "div", "ua-statbar", "");
        node(row, "span", "ua-statbar-label", label);
        node(row, "span", "ua-statbar-value", value);
        Element bar = node(row, "div", "ua-bar", "");
        Element fill = node(bar, "div", "ua-bar-fill", "");
        fill.setAttribute("style", "width:" + Math.max(0, Math.min(180, (int) Math.round(ratio * 180))) + "px");
        Element deltaElement = node(row, "span", "ua-statbar-delta", delta);
        if (delta.startsWith("-")) {
            deltaElement.setAttribute("class", "ua-statbar-delta negative");
        }
    }

    private String format(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
                ? String.valueOf((int) Math.rint(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private String delta(double value) {
        if (Math.abs(value) < 0.05) {
            return "";
        }
        return (value > 0 ? "+" : "") + format(value);
    }

    private void renderSlots(Element parent, ArmoryEntry entry, ArmoryEntry shown) {
        node(parent, "div", "ua-anno", "配件槽位 · 点击进入改装");
        GunEntry model = new GunEntry(0, shown.stack());
        List<AttachmentType> types = model.getSupportedSlotTypes();
        if (types.isEmpty()) {
            node(parent, "p", "ua-muted", "该枪械没有可改装槽位。");
            return;
        }
        Element grid = node(parent, "div", "ua-slots", "");
        for (int index = 0; index < types.size(); index++) {
            AttachmentType type = types.get(index);
            GunEntry.SlotInfo slot = model.getSlot(type);
            Element row = node(grid, "div", "ua-slot", "");
            row.setAttribute("tabindex", "0");
            Element icon = node(row, "div", "ua-slot-icon", "");
            boolean emptySlot = slot == null || slot.isEmpty() || slot.getEffectiveId() == null
                    || "empty".equals(slot.getEffectiveId().getPath());
            ItemStack attachment = emptySlot ? ItemStack.EMPTY : attachmentStack(slot.getEffectiveId());
            if (!attachment.isEmpty()) {
                Element iconPreview = preview(icon, attachment);
                iconPreview.setAttribute("class", "ua-slot-icon-item");
            }
            Element main = node(row, "div", "ua-slot-main", "");
            node(main, "span", "ua-slot-slot", slotLabel(type));
            node(main, "span", "ua-slot-name", emptySlot ? "-- 空槽位" : attachmentName(slot.getEffectiveId()));
            int slotIndex = index;
            row.addEventListener("click", event -> {
                attachmentSlot = slotIndex;
                detailTab = 1;
                renderPage();
            });
        }
    }

    private ItemStack attachmentStack(net.minecraft.resources.ResourceLocation id) {
        if (id == null) {
            return ItemStack.EMPTY;
        }
        try {
            return com.tacz.guns.api.item.builder.AttachmentItemBuilder.create().setId(id).build();
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private String attachmentName(net.minecraft.resources.ResourceLocation id) {
        if (id == null) {
            return "-- 空槽位";
        }
        return com.tacz.guns.api.TimelessAPI.getCommonAttachmentIndex(id)
                .map(index -> index.getPojo().getName())
                .map(name -> net.minecraft.network.chat.Component.translatable(name).getString())
                .orElse(id.getPath().replace('_', ' '));
    }

    private String slotLabel(AttachmentType type) {
        return switch (type) {
            case MUZZLE -> "枪口";
            case GRIP -> "握把";
            case SCOPE -> "瞄具";
            case STOCK -> "枪托";
            case EXTENDED_MAG -> "弹匣";
            case LASER -> "激光";
            default -> type.name();
        };
    }

    private void renderAmmo(Element parent, ArmoryEntry entry, ArmoryEntry shown) {
        node(parent, "div", "ua-anno", "弹药");
        String ammo = shown.ammoId();
        GunData data = shown.gunData();
        IGun gun = IGun.getIGunOrNull(shown.stack());
        int magazine = data == null ? 0
                : com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(shown.stack(), data);
        int current = gun == null ? 0 : gun.getCurrentAmmoCount(shown.stack());
        Element line = node(parent, "div", "ua-ammo-line", "");
        node(line, "span", "ua-card-name", ammo.isBlank() ? "未知口径" : ammo);
        node(line, "span", "ua-anno", current + " / " + magazine);
        Element mag = node(parent, "div", "ua-mag", "");
        Element fill = node(mag, "div", "ua-mag-fill", "");
        fill.setAttribute("style", "width:" + (magazine <= 0 ? 0
                : Math.max(0, Math.min(180, current * 180 / magazine))) + "px");
        BigInteger stock = BigInteger.ZERO;
        for (ArmoryEntry candidate : source(Page.HOME)) {
            if (candidate.kind() == ArmoryItemIdentity.Kind.AMMO
                    && java.util.Objects.equals(String.valueOf(candidate.resourceId()), ammo)) {
                stock = stock.add(candidate.quantity());
            }
        }
        Element kv = node(parent, "div", "ua-kv", "");
        node(kv, "span", "", "库存弹药");
        node(kv, "span", "", stock + " 发");
        Element row = node(parent, "div", "ua-toolbar-row", "");
        button(row, "补满", () -> submitEquipment("reload", entry, new CompoundTag()))
                .setDisabled(equipmentPending || !owns(entry));
        button(row, "领取 64 发", () -> supplyAmmo(entry))
                .setDisabled(equipmentPending || ArmoryClientState.position() == null);
        button(row, "制作", () -> openCraftDialog(ammoEntry(entry), null));
    }

    private void renderCode(Element parent, ArmoryEntry entry, ArmoryEntry shown) {
        node(parent, "div", "ua-tree-sep", "");
        node(parent, "div", "ua-anno", "改枪码");
        Element code = node(parent, "div", "ua-code", shortCode(shown));
        String exported = GunTemplateCodecs.share().exportCode(shown.stack(), entry.name());
        Element row = node(parent, "div", "ua-toolbar-row", "");
        button(row, "复制", () -> copyCode(shown));
        button(row, "复制完整码", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(exported);
            message("完整改枪码已复制。");
        });
        node(parent, "div", "ua-anno", "导入改枪码");
        Element input = node(parent, "textarea", "form-textarea", "");
        input.setValue(detailCodeDraft);
        input.setAttribute("placeholder", "粘贴同型号改枪码");
        input.setAttribute("style", "height:64px;min-height:64px");
        input.addEventListener("input", event -> detailCodeDraft = input.getValue());
        Element actions = node(parent, "div", "ua-toolbar-row", "");
        button(actions, "预览", () -> previewCode(entry, input.getValue()));
        button(actions, "应用到库存", () -> {
            CompoundTag operation = new CompoundTag();
            operation.putString("Code", input.getValue().strip());
            submitEquipment("apply-code", entry, operation);
        }).setDisabled(!owns(entry) || entry.attachmentLocked() || equipmentPending);
    }

    private void renderImport(Element content) {
        Element wrap = node(content, "div", "ua-import", "");
        renderSubtabs(wrap);
        Element grid = node(wrap, "div", "ua-import-grid", "");

        Element left = panel(grid, "ua-import-left");
        panelHead(left, "粘贴改枪码", null, null);
        Element leftBody = panelBody(left);
        Element input = node(leftBody, "textarea", "form-textarea", "");
        input.setAttribute("placeholder", "UAT2:... 一行一个");
        Element actions = node(leftBody, "div", "ua-toolbar-row", "");
        button(actions, "从剪贴板粘贴", () -> input.setValue(
                Minecraft.getInstance().keyboardHandler.getClipboard()));
        button(actions, "导入并预览", () -> {
            try {
                String text = input.getValue().strip();
                if (text.length() > 65536) {
                    throw new IllegalArgumentException("改枪码过长");
                }
                ItemStack stack = GunTemplateCodecs.share().createPreviewStack(
                        GunTemplateCodecs.share().importCode(text));
                openDetails(new ArmoryEntry(ArmoryItemIdentity.key(stack), stack, BigInteger.ZERO,
                        List.of(), ArmoryEntry.Origin.IMPORT, -1));
            } catch (IllegalArgumentException exception) {
                message("无法导入：" + exception.getMessage());
            }
        });

        Element right = panel(grid, "ua-import-right");
        panelHead(right, "解析预览", null, null);
        Element rightBody = panelBody(right);
        emptyState(rightBody, "等待改枪码", "粘贴后在这里预览，导入只生成预览，不直接生成物品");
    }


    private void endSession() {
        if (!ended) {
            ended = true;
            closeEquipmentDialog();
            ContextMenu.closeActive();
            ArmoryNetwork.request("close", -1, "", 0, 0);
            ArmoryClientState.close();
            if (Minecraft.getInstance().player != null) {
                ArmoryManager.unbind(Minecraft.getInstance().player);
            }
        }
    }

    @Override
    public void onClose() {
        endSession();
        super.onClose();
    }

    @Override
    public void removed() {
        endSession();
        super.removed();
    }

    private record Route(Page page, ArmoryEntry entry, int index) { }
    private enum View { PREVIEW, GRID, LIST, TABLE, COMPACT }
    private enum GroupBy {
        TYPE("类型 / 槽位"), AMMO("口径"), NAMESPACE("来源模组"), FAVORITE("收藏"), ORIGIN("来源");
        private final String label;
        GroupBy(String label) { this.label = label; }
    }
    private enum Page {
        HOME("军械库主页"), INVENTORY("库存管理"), CATALOGUE("军备总览"), FAVORITE("收藏夹"),
        RECENT("最近浏览"), IMPORT("改枪码导入"), DETAIL("枪械详情");
        private final String title;
        Page(String title) { this.title = title; }
    }
}
