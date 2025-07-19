package org.mvplugins.multiverse.core.commands;

import co.aikar.commands.annotation.*;
import io.vavr.control.Try;
import jakarta.inject.Inject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jvnet.hk2.annotations.Service;
import org.mvplugins.multiverse.core.command.MVCommandIssuer;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.bukkit.entity.SpawnCategory;
import org.mvplugins.multiverse.core.world.entity.EntityPurger;

import java.util.*;
import java.util.stream.Collectors;

@Service
@CommandAlias("mv")
@Subcommand("purge")
@CommandPermission("multiverse.core.purge")
public class PurgeCommand extends CoreCommand {

    @Inject
    public PurgeCommand() {
    }

    @Default
    @Syntax("<world> <entities>")
    @CommandCompletion("@mvworlds:scope=loaded @purgeTypes:multiple,resolveUntil=arg1")
    public void onPurgeCommand(MVCommandIssuer issuer,
                               @Flags("resolve=issuerAware") LoadedMultiverseWorld world,
                               String entityList) {

        World bukkitWorld = world.getBukkitWorld().getOrNull();
        if (bukkitWorld == null) {
            issuer.sendError("Could not access the Bukkit world. Is the world loaded?");
            return;
        }

        Set<String> inputArgs = Arrays.stream(entityList.split(","))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        boolean removeAll = inputArgs.contains("all");

        Set<SpawnCategory> categoriesToRemove = inputArgs.stream()
                .map(arg -> Try.of(() -> SpawnCategory.valueOf(arg.toUpperCase())).getOrNull())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int removed = removeAll
                ? EntityPurger.purgeAllEntities(world)
                : !categoriesToRemove.isEmpty()
                ? EntityPurger.purgeEntities(world, categoriesToRemove.toArray(new SpawnCategory[0]))
                : (int) bukkitWorld.getEntities().stream()
                .filter(entity -> !(entity instanceof org.bukkit.entity.Player))
                .filter(entity -> inputArgs.contains(entity.getType().name().toLowerCase()))
                .peek(Entity::remove)
                .count();

        issuer.sendMessage("Purged " + removed + " entities from world " + world.getName() + ".");

    }
    private static Set<EntityType> expandTypesOrCategories(String input) {
        Set<EntityType> types = new HashSet<>();

        // Try to match a SpawnCategory (e.g., MONSTER, ANIMAL)
        try {
            SpawnCategory category = SpawnCategory.valueOf(input.toUpperCase(Locale.ROOT));
            for (EntityType type : EntityType.values()) {
                try {
                    // Not all entity types have a spawn category
                    if (type.getEntityClass() != null) {
                        Entity dummy = Bukkit.getWorlds().get(0).spawnEntity(
                                new Location(Bukkit.getWorlds().get(0),0,0,0),
                                type
                        );
                        if (dummy.getSpawnCategory() == category) {
                            types.add(type);
                        }
                        dummy.remove();
                        }
                    }catch (Exception ignored) {}
                }
            return types;
            } catch (IllegalArgumentException ignored) {
            // Not a Valid category
        }

        // Try to match as an EntityType
        try {
            types.add(EntityType.valueOf(input.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {}

        return types;
    }
}