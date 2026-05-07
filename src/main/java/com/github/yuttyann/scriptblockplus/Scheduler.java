/**
 * ScriptBlockPlus - Allow you to add script to any blocks.
 * Copyright (C) 2021 yuttyann44581
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.github.yuttyann.scriptblockplus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class Scheduler {

    private final Plugin plugin;

    public Scheduler(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    public Plugin getPlugin() {
        return plugin;
    }

    @NotNull
    public BukkitTask run(@NotNull Runnable task) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runGlobal(plugin, task, 0L);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    @NotNull
    public BukkitTask run(@NotNull Runnable task, final long delay) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runGlobal(plugin, task, delay);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    @NotNull
    public BukkitTask run(@NotNull Runnable task, final long delay, final long period) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runGlobalRepeated(plugin, task, delay, period);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    @NotNull
    public BukkitTask runAtEntity(@NotNull Entity entity, @NotNull Runnable task) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtEntity(plugin, entity, task, 0L);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    @NotNull
    public BukkitTask runAtEntity(@NotNull Entity entity, @NotNull Runnable task, final long delay) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtEntity(plugin, entity, task, delay);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    @NotNull
    public BukkitTask runAtEntityRepeated(@NotNull Entity entity, @NotNull Runnable task, final long delay, final long period) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtEntityRepeated(plugin, entity, task, delay, period);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }

    @NotNull
    public BukkitTask runAtLocation(@NotNull Location location, @NotNull Runnable task) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtLocation(plugin, location, task, 0L);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTask(plugin, task);
    }

    @NotNull
    public BukkitTask runAtLocation(@NotNull Location location, @NotNull Runnable task, final long delay) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtLocation(plugin, location, task, delay);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    @NotNull
    public BukkitTask runAtLocationRepeated(@NotNull Location location, @NotNull BukkitRunnable task, final long delay, final long period) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAtLocationRepeated(plugin, location, task, delay, period);
            return new DummyBukkitTask();
        }
        return task.runTaskTimer(plugin, delay, period);
    }

    @NotNull
    public BukkitTask asyncRun(@NotNull Runnable task) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAsync(plugin, task, 0L);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @NotNull
    public BukkitTask asyncRun(@NotNull Runnable task, final long delay) {
        if (FoliaCompat.isFolia()) {
            FoliaCompat.runAsync(plugin, task, delay);
            return new DummyBukkitTask();
        }
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay);
    }

    @NotNull
    public CompletableFuture<Void> teleportPlayer(@NotNull Player player, @NotNull Location location) {
        return FoliaCompat.teleport(player, location);
    }

    private static class DummyBukkitTask implements BukkitTask {
        @Override
        public int getTaskId() {
            return -1;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void cancel() {
        }
    }
}
