package org.mvplugins.multiverse.core.config.node;

import java.util.Collection;
import java.util.List;

import io.vavr.control.Try;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.mvplugins.multiverse.core.config.node.serializer.NodeSerializer;

/**
 * A node that holds a list of values of a specific type.
 *
 * @param <I>   The type of list item.
 */
public interface ListValueNode<I> extends ValueNode<List<I>> {

    /**
     * Gets the class type of list item.
     *
     * @return The class type of list item.
     */
    @NotNull Class<I> getItemType();

    /**
     * Suggests possible string values for this node.
     *
     * @param input The input string.
     * @return A collection of possible string values.
     */
    @NotNull Collection<String> suggestItem(@Nullable String input);

    /**
     * Suggests possible string values for this node. Use contextural information from the sender such as
     * sender name, permissions, or player location for better suggestions.
     *
     * @param sender    The sender context.
     * @param input     The input string.
     * @return A collection of possible string values
     *
     * @since 5.1
     */
    @ApiStatus.AvailableSince("5.1")
    default @NotNull Collection<String> suggestItem(@NotNull CommandSender sender, @Nullable String input) {
        return suggestItem(input);
    }

    /**
     * Parses the given string into a value of type {@link I}. Used for property set by user input.
     *
     * @param input The string to parse.
     * @return The parsed value, or given exception if parsing failed.
     */
    @NotNull Try<I> parseItemFromString(@Nullable String input);

    /**
     * Parses the given string into a value of type {@link I}. Used for property set by user input.
     *
     * @param sender    The sender context.
     * @param input     The string to parse.
     * @return The parsed value, or given exception if parsing failed.
     *
     * @since 5.1
     */
    @ApiStatus.AvailableSince("5.1")
    default @NotNull Try<I> parseItemFromString(@NotNull CommandSender sender, @Nullable String input) {
        return parseItemFromString(input);
    }

    /**
     * Gets the serializer for this node.
     *
     * @return  The serializer for this node.
     */
    @Nullable NodeSerializer<I> getItemSerializer();

    /**
     * Validates the value of this node.
     *
     * @param value The value to validate.
     * @return A successful try if validation passed, else the exception object throw.
     */
    Try<Void> validateItem(@Nullable I value);

    /**
     * Called when the value of this node is set.
     *
     * @param oldValue The old value.
     * @param newValue The new value.
     */
    void onSetItemValue(@Nullable I oldValue, @Nullable I newValue);
}
