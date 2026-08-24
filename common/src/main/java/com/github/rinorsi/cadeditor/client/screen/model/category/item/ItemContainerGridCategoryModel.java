package com.github.rinorsi.cadeditor.client.screen.model.category.item;

import com.github.rinorsi.cadeditor.client.screen.model.ItemEditorModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.EntryModel;
import com.github.rinorsi.cadeditor.client.screen.model.entry.item.ItemContainerSlotEntryModel;
import com.github.rinorsi.cadeditor.common.ModTexts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

public class ItemContainerGridCategoryModel extends ItemEditorCategoryModel {
    public ItemContainerGridCategoryModel(ItemEditorModel parent) {
        super(ModTexts.CONTAINER_GRID, parent);
    }

    @Override
    protected void setupEntries() {
        ItemStack stack = getParent().getContext().getItemStack();
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        //TODO Add real grid drag-and-drop and batch editing; current UX is tedious (scheduled later)
        if (contents != null) {
            contents.stream().forEach(item -> getEntries().add(new ItemContainerSlotEntryModel(this, item)));
        }
    }

    @Override
    public boolean canAddEntryInList() {
        return true;
    }

    //TODO Add a performance warning for large containers so players don't freeze the game

    @Override
    public int getEntryListStart() {
        return 0;
    }

    @Override
    public EntryModel createNewListEntry() {
        return new ItemContainerSlotEntryModel(this, ItemStack.EMPTY);
    }

    @Override
    public void apply() {
        super.apply();
        ItemStack stack = getParent().getContext().getItemStack();
        List<ItemStack> collected = new ArrayList<>();
        boolean anyNonEmpty = false;
        for (EntryModel entryModel : getEntries()) {
            if (!(entryModel instanceof ItemContainerSlotEntryModel entry)) {
                continue;
            }
            ItemStack slotStack = entry.getItemStack();
            ItemStack copy = slotStack.isEmpty() ? ItemStack.EMPTY : slotStack.copy();
            collected.add(copy);
            if (!copy.isEmpty()) {
                anyNonEmpty = true;
            }
        }
        if (!anyNonEmpty) {
            stack.remove(DataComponents.CONTAINER);
        } else {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(collected));
        }
    }
}
