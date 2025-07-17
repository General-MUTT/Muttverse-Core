package org.mvplugins.multiverse.core.commands;

import co.aikar.commands.annotation.*;
import jakarta.inject.Inject;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jvnet.hk2.annotations.Service;
import org.mvplugins.multiverse.core.command.MVCommandIssuer;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@Service
@CommandAlias("mv")
@Subcommand("purge")
@CommandPermission("multiverse.core.purge")
public class PurgeCommand extends CoreCommand {

    @Inject
    public PurgeCommand() {}

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

        Set<String> toRemove = Arrays.stream(entityList.split(","))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        boolean removeAll = toRemove.contains("all");
        int removed = 0;

        for (Entity entity : bukkitWorld.getEntities()) {
            EntityType type = entity.getType();
            if ((removeAll && type.isAlive()) || toRemove.contains(type.name().toLowerCase())) {
                entity.remove();
                removed++;
            }
        }

        issuer.sendMessage("Purged " + removed + " entities from world " + world.getName() + ".");
    }
}
