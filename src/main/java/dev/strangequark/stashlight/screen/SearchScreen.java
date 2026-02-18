package dev.strangequark.stashlight.screen;

import dev.strangequark.stashlight.config.Config;
import dev.strangequark.stashlight.gui.ItemGrid;
import dev.strangequark.stashlight.logic.filter.*;
import dev.strangequark.stashlight.logic.sort.SortManager;
import dev.strangequark.stashlight.model.IndexedItem;
import dev.strangequark.stashlight.repository.ContainerRepository;
import dev.strangequark.stashlight.util.Util;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.strangequark.stashlight.gui.UIStyle.*;

public class SearchScreen extends BaseOwoScreen<FlowLayout> {
    private final ContainerRepository repository;
    private FlowLayout rootComponent;
    private FlowLayout mainWindow;
    private TextBoxComponent searchField;
    private ItemGrid itemGrid;

    private final FilterManager filterManager = new FilterManager();
    private final SortManager sortManager;

    private static final long DEBOUNCE_MS = 150;
    private String pendingQuery = null;
    private long lastQueryChangeTime = 0;

    public SearchScreen(ContainerRepository repository) {
        this.repository = repository;
        this.sortManager = new SortManager(Config.get().sortKey());
        setupFilters();
    }

    private void setupFilters() {
        List<FilterStrategy> strategies = new ArrayList<>();
        var world = Minecraft.getInstance().level;
        String currentDim;

        if (world != null) {
            currentDim = Util.getDimensionName(world);
            strategies.add(new DimensionFilter("Current", currentDim));
        }

        strategies.add(new DimensionFilter("All", null));

        repository.getDimensions().forEach(dim -> strategies.add(new DimensionFilter(dim, dim)));

        filterManager.setCyclingStrategies(strategies);
        filterManager.addAlwaysOn(new SmallContainerFilter());
        filterManager.addAlwaysOn(new RadiusFilter());
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void init() {
        super.init();
        if (this.rootComponent.focusHandler() != null && this.searchField.focusHandler() != null) {
            this.rootComponent.focusHandler().focus(this.searchField, UIComponent.FocusSource.MOUSE_CLICK);
            this.searchField.setHighlightPos(0);
            this.searchField.moveCursorToEnd(false);
        }
        this.rootComponent.queue(() -> this.refreshGrid(this.searchField.getValue()));
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        this.rootComponent = rootComponent;
        var config = Config.get();

        // --- 1. MAIN WINDOW ---
        this.mainWindow = (FlowLayout) UIContainers
                .verticalFlow(Sizing.fill(SCREEN_FILL_PERCENT), Sizing.fill(SCREEN_FILL_PERCENT))
                .gap(GAP)
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(PADDING));

        // --- 2. HEADER & SEARCH BAR ---
        LabelComponent title = UIComponents.label(Component.translatable("screen.stashlight.label.searchContainers")).shadow(true);

        FlowLayout searchBar = (FlowLayout) UIContainers
                .horizontalFlow(Sizing.fill(), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        ButtonComponent sortBtn = (ButtonComponent) UIComponents
                .button(Component.literal(sortManager.getCurrent().getLabel()), b -> {
                    sortManager.cycle();
                    b.setMessage(Component.literal(sortManager.getCurrent().getLabel()));
                    b.tooltip(sortManager.getCurrent().getTooltip());
                    config.setSortKey(sortManager.getCurrent().key());
                    refreshGrid(searchField.getValue());
                })
                .tooltip(sortManager.getCurrent().getTooltip())
                .sizing(Sizing.fixed(COMPONENT_HEIGHT), Sizing.fixed(COMPONENT_HEIGHT));

        this.searchField = UIComponents.textBox(Sizing.fixed(SEARCH_WIDTH), config.searchQuery());
        this.searchField.setMaxLength(100);
        this.searchField.onChanged().subscribe(text -> {
            config.setSearchQuery(text);
            // Don't rebuild immediately — record the change and let the debounce
            // in render() fire refreshGrid once typing has settled.
            this.pendingQuery = text;
            this.lastQueryChangeTime = System.currentTimeMillis();
        });

        ButtonComponent dimFilterBtn = (ButtonComponent) UIComponents.button(
                        Component.translatable("gui.stashlight.label.dimension").append(": ").append(filterManager.getCurrentLabel()),
                        b -> {
                            filterManager.cycle();
                            b.setMessage(Component.translatable("gui.stashlight.label.dimension").append(": ").append(filterManager.getCurrentLabel()));
                            refreshGrid(searchField.getValue());
                        })
                .sizing(Sizing.fixed(FILTER_WIDTH), Sizing.fixed(COMPONENT_HEIGHT));

        searchBar.child(sortBtn).child(this.searchField).child(dimFilterBtn);

        // --- 3. SCROLLABLE GRID ---
        FlowLayout gridWrapper = (FlowLayout) UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand(100))
                .surface(Surface.outline(GRID_BORDER))
                .padding(Insets.of(BORDER));

        this.itemGrid = new ItemGrid();

        FlowLayout scrollContent = (FlowLayout) UIContainers
                .verticalFlow(Sizing.content(), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.CENTER);

        scrollContent.child(this.itemGrid);

        ScrollContainer<FlowLayout> scrollContainer = UIContainers
                .verticalScroll(Sizing.fill(100), Sizing.fill(100), scrollContent);

        scrollContainer
                .scrollbarThiccness(SCROLL_WIDTH)
                .scrollbar(ScrollContainer.Scrollbar.vanillaFlat())
                .surface((drawContext, component) -> {
                    int x1 = component.x() + component.width() - SCROLL_WIDTH;
                    int y1 = component.y();
                    int x2 = component.x() + component.width();
                    int y2 = component.y() + component.height();
                    drawContext.fill(x1, y1, x2, y2, SCROLL_TRACK);
                });

        gridWrapper.child(scrollContainer);

        // --- 4. FOOTER ---
        FlowLayout footer = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(COMPONENT_HEIGHT))
                .gap(GAP)
                .verticalAlignment(VerticalAlignment.CENTER);

        CheckboxComponent lookAtCheckbox = (CheckboxComponent) UIComponents
                .checkbox(Component.translatable("screen.stashlight.lookAtTarget"))
                .checked(config.lookAtTarget()).onChanged(config::setLookAtTarget)
                .margins(Insets.top(BORDER));

        CheckboxComponent showSmallCheckbox = (CheckboxComponent) UIComponents
                .checkbox(Component.translatable("screen.stashlight.showSmallContainers"))
                .checked(config.showSmallContainers())
                .onChanged(v -> {
                    config.setShowSmallContainers(v);
                    refreshGrid(searchField.getValue());
                })
                .margins(Insets.top(BORDER));


        DiscreteSliderComponent distanceSlider = UIComponents.discreteSlider(Sizing.fixed(SLIDER_WIDTH), 0, 5);
        distanceSlider.snap(true).decimalPlaces(0);

        distanceSlider.setFromDiscreteValue(config.searchRadiusIndex());
        distanceSlider.message(s -> RadiusFilter.getLabelForIndex(config.searchRadiusIndex()));

        distanceSlider.onChanged().subscribe(v -> {
            int index = (int) Math.round(v);
            if (index == config.searchRadiusIndex()) {
                return;
            }
            config.setSearchRadiusIndex(index);
            distanceSlider.message(s -> RadiusFilter.getLabelForIndex(config.searchRadiusIndex()));
            refreshGrid(searchField.getValue());
        });

        footer.child(distanceSlider).child(lookAtCheckbox).child(showSmallCheckbox);

        // --- ASSEMBLE ---
        mainWindow.child(title).child(searchBar).child(gridWrapper).child(footer);
        rootComponent.child(mainWindow).alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
    }

    private void refreshGrid(String query) {
        int windowWidth = (int) (this.width * (SCREEN_FILL_PERCENT / 100.0));
        // Subtract mainWindow padding (×2), scrollbar width, and gap (reserved for ItemGrid's internal padding)
        int availableVars = (PADDING * 2) + SCROLL_WIDTH + GAP + (BORDER * 2);
        int availableWidth = windowWidth - availableVars;
        int slotsPerRow = Math.max(1, availableWidth / (SLOT_SIZE + GAP));

        // Snap window width to exactly fit the columns
        int contentWidth = slotsPerRow * (SLOT_SIZE + GAP) + GAP;
        int snappedWidth = contentWidth + availableVars;
        if (this.mainWindow != null) {
            this.mainWindow.horizontalSizing(Sizing.fixed(snappedWidth));
        }

        final String lowerQuery = query.toLowerCase();

        List<IndexedItem> sortedItems = repository.getSearchIndex().stream()
                .filter(item -> matchesDeep(item, lowerQuery) && filterManager.matches(item))
                .collect(Collectors.toCollection(ArrayList::new));

        if (sortManager.getCurrent() != null) sortManager.getCurrent().sort(sortedItems);

        this.itemGrid.setItems(sortedItems, slotsPerRow);
    }

    public static boolean matchesDeep(IndexedItem item, String lowerQuery) {
        if (lowerQuery.isEmpty()) return true;

        // 1. Check main item name (searchKey is already lowercase)
        if (item.searchKey().contains(lowerQuery)) return true;

        // 2. Check Shulker-like containers
        var container = item.stack().get(DataComponents.CONTAINER);
        if (container != null) {
            for (ItemStack inner : container.nonEmptyItems()) {
                if (inner.getHoverName().getString().toLowerCase().contains(lowerQuery)) return true;
            }
        }

        // 3. Check Bundles
        var bundle = item.stack().get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) {
                if (inner.getHoverName().getString().toLowerCase().contains(lowerQuery)) return true;
            }
        }

        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Debounce: fire refreshGrid only after typing has settled for DEBOUNCE_MS.
        if (pendingQuery != null && System.currentTimeMillis() - lastQueryChangeTime >= DEBOUNCE_MS) {
            refreshGrid(pendingQuery);
            pendingQuery = null;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.blurBeforeThisStratum();
        super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.refreshGrid(this.searchField.getValue());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}