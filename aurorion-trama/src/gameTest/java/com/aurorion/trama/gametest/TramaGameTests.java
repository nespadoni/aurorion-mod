package com.aurorion.trama.gametest;

import com.aurorion.core.character.*;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.trama.config.TramaConfig;
import com.aurorion.trama.progression.ProgressData;
import com.aurorion.trama.server.TramaEvents;
import com.aurorion.trama.server.TramaRuntime;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.puffish.skillsmod.api.*;
import java.util.UUID;

@Mod("aurorion_trama_tests")
@GameTestHolder("aurorion_trama_tests")
@PrefixGameTestTemplate(false)
public final class TramaGameTests {
    @GameTest(template="empty") public static void catalogAndOrigins(GameTestHelper h) {
        var c = SkillsAPI.getCategory(TramaRuntime.CATEGORY).orElseThrow();
        h.assertTrue(c.streamSkills().count()==271,"Pufferfish parses all 271 nodes and connections");
        try (var t = player(h,"Origins")) {
            var p=t.player;
            h.assertTrue(!c.isUnlocked(p),"No house means no accessible category");
            String[] houses={"ignivar","sylvara","nyx","venthra","aetheris"};
            String[] roots={"I00","S00","N00","V00","A00"};
            for (int i=0;i<houses.length;i++) {
                HouseManager.assign(p.server,p.getUUID(),ResourceLocation.parse("aurorion_ethereal:"+houses[i]));
                h.assertTrue(TramaRuntime.sync(p),"House opens category");
                h.assertTrue(c.streamUnlockedSkills(p).count()==1,"Exactly one unlocked origin after house change");
                h.assertTrue(c.getSkill(roots[i]).orElseThrow().getState(p)==Skill.State.UNLOCKED,"Correct origin");
                h.assertTrue(c.getPoints(p,TramaRuntime.POINTS)==5 && c.getSpentPoints(p)==0,"Five initial points; origin costs zero");
                TramaRuntime.sync(p); TramaRuntime.sync(p);
                h.assertTrue(c.getPointsTotal(p)==5,"Repeated reconciliation never grants extra points");
            }
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void discoveryTimeGatesAndRespec(GameTestHelper h) {
        try (var t=player(h,"Formation")) {
            var p=t.player; house(p,"ignivar");
            var c=SkillsAPI.getCategory(TramaRuntime.CATEGORY).orElseThrow();
            var e=TramaRuntime.entry(p);
            TramaRuntime.advancement(p,ResourceLocation.parse("minecraft:story/mine_stone"));
            long xp=e.experience;
            TramaRuntime.advancement(p,ResourceLocation.parse("minecraft:story/mine_stone"));
            TramaRuntime.advancement(p,ResourceLocation.parse("minecraft:recipes/tools/stone_pickaxe"));
            h.assertTrue(e.experience==xp && xp==150,"Only allowlisted, first-time discoveries grant XP");
            h.assertTrue(c.getPointsTotal(p)==5,"Discovery XP cannot bypass active time");
            e.activeSeconds=900; TramaRuntime.sync(p);
            h.assertTrue(c.getPointsTotal(p)==6,"First additional point requires both time and XP");
            c.getSkill("IT1").orElseThrow().unlock(p);
            c.getSkill("IB1").orElseThrow().unlock(p);
            TramaRuntime.refresh(p);
            h.assertTrue(c.getSpentPoints(p)==2,"Pufferfish charges each bought skill");
            h.assertTrue(TramaRuntime.respec(p)==1,"First respec allowed outside combat");
            h.assertTrue(c.getSpentPoints(p)==0 && c.getPointsTotal(p)==6 && c.streamUnlockedSkills(p).count()==1,"Respec refunds and restores only origin");
            h.assertTrue(e.experience==xp && e.activeSeconds==900,"Respec preserves formation");
            h.assertTrue(TramaRuntime.respec(p)==0,"Persisted respec cooldown enforced");
            TramaRuntime.logout(p.getUUID()); TramaRuntime.sync(p);
            h.assertTrue(TramaRuntime.respec(p)==0 && c.getPointsTotal(p)==6,"Reconnect does not reset respec or duplicate points");
            double armor=p.getAttributeValue(Attributes.ARMOR);
            h.assertTrue(armor==0,"Respec removes tree armor");
            e.lastRespec=0; e.combatUntil=System.currentTimeMillis()+15000;
            h.assertTrue(TramaRuntime.respec(p)==0,"Combat blocks even the first free respec");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void characterResetOnlineAndOffline(GameTestHelper h) {
        try (var t=player(h,"Characters")) {
            var p=t.player; house(p,"nyx");
            var chars=CharacterData.get(p.server);
            var c=SkillsAPI.getCategory(TramaRuntime.CATEGORY).orElseThrow();
            var old=TramaRuntime.entry(p); old.experience=20000; old.activeSeconds=300000; TramaRuntime.sync(p);
            c.getSkill("NT1").orElseThrow().unlock(p);
            var unrelated=ResourceLocation.parse("aurorion_trama:old_character_extra"); c.setPoints(p,unrelated,10);
            chars.markDead(p.getUUID()); chars.authorize(p.getUUID());
            var tx=chars.beginReplacement(p.getUUID(),"Characters",new CharacterName("Novo","Personagem"));
            // Direct handler call represents an offline retry; it must mark the category for erasure.
            TramaEvents.reset(new CharacterResetEvent(p.server,tx));
            TramaEvents.reset(new CharacterResetEvent(p.server,tx));
            chars.finishReplacement(p.getUUID(),tx.next().id());
            TramaRuntime.sync(p);
            var next=TramaRuntime.entry(p);
            h.assertTrue(next.character.equals(tx.next().id()) && next.experience==0 && next.activeSeconds==0,"New identity starts with new formation");
            h.assertTrue(c.getPointsTotal(p)==5 && c.getPoints(p,unrelated)==0 && c.getSpentPoints(p)==0,"No previous build or point source survives");
            h.assertTrue(c.streamUnlockedSkills(p).count()==1,"New character keeps only current house origin");
            // Offline reservation: no network player is registered in the server list for this account.
            UUID account=UUID.randomUUID(); chars.current(account); chars.markDead(account); chars.authorize(account);
            var offline=chars.beginReplacement(account,"Offline",new CharacterName("Outra","Historia"));
            var data=ProgressData.get(p.server); data.replace(account,offline.previousId()).experience=20000;
            TramaEvents.reset(new CharacterResetEvent(p.server,offline));
            TramaEvents.reset(new CharacterResetEvent(p.server,offline));
            var entry=data.find(account);
            h.assertTrue(entry.character.equals(offline.next().id()) && entry.eraseCategory && entry.experience==0,"Offline retries retain the erasure marker and new identity");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void pointsAttributesAndPersistence(GameTestHelper h) {
        try (var t=player(h,"Persistence")) {
            var p=t.player; house(p,"venthra");
            var c=SkillsAPI.getCategory(TramaRuntime.CATEGORY).orElseThrow();
            var e=TramaRuntime.entry(p); e.experience=20000; e.activeSeconds=300000; TramaRuntime.sync(p);
            h.assertTrue(c.getPointsTotal(p)==45,"Cumulative progression stops at 45");
            c.getSkill("VX1").orElseThrow().unlock(p);
            TramaRuntime.refresh(p);
            double speed=p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            for(int i=0;i<10;i++) TramaRuntime.refresh(p);
            h.assertTrue(Math.abs(speed-p.getAttributeValue(Attributes.MOVEMENT_SPEED))<1e-12,"Repeated application never stacks modifiers");
            h.assertTrue(speed>p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),"Unlocked travel rating actually changes movement attribute");
            var tag=ProgressData.get(p.server).save(new net.minecraft.nbt.CompoundTag(),p.server.registryAccess());
            var entries=tag.getList("Players",net.minecraft.nbt.Tag.TAG_COMPOUND);
            boolean found=false;
            for(int i=0;i<entries.size();i++) if(entries.getCompound(i).getUUID("Player").equals(p.getUUID())) {
                var saved=entries.getCompound(i); found=saved.getLong("Experience")==20000 && saved.getLong("ActiveSeconds")==300000 && saved.getInt("Earned")==45;
            }
            h.assertTrue(found,"SavedData writes progression under the actual account UUID");
            HouseManager.clear(p.server,p.getUUID());
            h.assertTrue(!c.isUnlocked(p) && p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ResourceLocation.parse("aurorion_trama:mov"))==null,"Removing house locks category and removes effects");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void persistedLedgerRoundTrip(GameTestHelper h) throws Exception {
        var server=h.getLevel().getServer(); UUID account=UUID.randomUUID(),character=UUID.randomUUID();
        var data=ProgressData.get(server); var e=data.replace(account,character);
        e.experience=4321; e.activeSeconds=7654; e.earned=17; e.origin="N00";
        e.practiceDay=20100; e.dailyPracticeXp=900; e.lastRespec=123456; e.combatUntil=567890;
        e.biomes.add("minecraft:plains"); e.advancements.add("minecraft:story/mine_stone");
        var tag=data.save(new net.minecraft.nbt.CompoundTag(),server.registryAccess());
        var load=ProgressData.class.getDeclaredMethod("load",net.minecraft.nbt.CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var loaded=(ProgressData)load.invoke(null,tag,server.registryAccess()); var copy=loaded.find(account);
        h.assertTrue(copy.character.equals(character) && copy.experience==4321 && copy.activeSeconds==7654 && copy.earned==17,"NBT reload preserves identity, XP, active time and earned points");
        h.assertTrue(copy.origin.equals("N00") && copy.eraseCategory && copy.practiceDay==20100 && copy.dailyPracticeXp==900,"NBT reload preserves pending erase and daily cap");
        h.assertTrue(copy.lastRespec==123456 && copy.combatUntil==567890 && copy.biomes.equals(e.biomes) && copy.advancements.equals(e.advancements),"NBT reload preserves cooldowns and first-time reward deduplication");
        h.succeed();
    }
    @GameTest(template="empty") public static void damageAndMagicAllowlist(GameTestHelper h) {
        try(var t=player(h,"Damage")) {
            var p=t.player; house(p,"nyx");
            var c=SkillsAPI.getCategory(TramaRuntime.CATEGORY).orElseThrow();
            c.getSkill("NW1").orElseThrow().unlock(p); // numeric MAG +1
            TramaRuntime.refresh(p);
            var registry=h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
            var magic=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,ResourceLocation.parse("minecraft:indirect_magic"));
            var source=new net.minecraft.world.damagesource.DamageSource(registry.getHolderOrThrow(magic),p,p);
            var mob=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.setHealth(100);
            long xp=TramaRuntime.entry(p).experience;
            mob.hurt(source,10);
            h.assertTrue(Math.abs((100-mob.getHealth())-10.06)<.001,"Real damage event applies exactly one MAG rating");
            mob.invulnerableTime=0; mob.setHealth(1); mob.hurt(source,10);
            h.assertTrue(TramaRuntime.entry(p).experience==xp,"Killing a mob grants zero formation XP");
            if(net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks")) {
                for(String type:new String[]{"blood","eldritch","ender","evocation","fire","holy","ice","lightning","nature"}) {
                    var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,ResourceLocation.parse("irons_spellbooks:"+type+"_magic"));
                    var spell=new net.minecraft.world.damagesource.DamageSource(registry.getHolderOrThrow(key),p,p);
                    var target=net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
                    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.setHealth(100);
                    target.hurt(spell,10);
                    h.assertTrue(Math.abs((100-target.getHealth())-10.06)<.001,"Iron's "+type+" magic gets one MAG bonus without melee/projectile stacking");
                }
            }
        }
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=2800) public static void activePracticeAndAfk(GameTestHelper h) {
        var active=player(h,"Active"); var afk=player(h,"Afk");
        house(active.player,"sylvara"); house(afk.player,"sylvara");
        active.player.setNoGravity(true); afk.player.setNoGravity(true);
        var entry=TramaRuntime.entry(active.player);
        entry.practiceDay=Math.floorDiv(System.currentTimeMillis(),86400000L);
        entry.dailyPracticeXp=TramaConfig.PRACTICE_DAILY_CAP.get()-5;
        for(int i=0;i<TramaConfig.BIOME_LIMIT.get();i++) entry.biomes.add("test:biome"+i);
        var origin=h.absolutePos(net.minecraft.core.BlockPos.ZERO);
        h.onEachTick(() -> {
            long second=h.getLevel().getGameTime()/20;
            active.player.setPos(origin.getX()+.5+(second%8)*2,origin.getY()+1,origin.getZ()+.5);
            active.player.setYRot(second%2==0 ? 0 : 60);
            // Embedded connections are not in ServerConnectionListener; drive the
            // normal Player.tick event path that a real network listener invokes.
            active.player.doTick(); afk.player.doTick();
        });
        h.runAtTickTime(2650,() -> {
            try {
                h.assertTrue(entry.activeSeconds>=120,"Real player ticks recognize two active minutes; actual="+entry.activeSeconds);
                h.assertTrue(entry.experience==5 && entry.dailyPracticeXp==TramaConfig.PRACTICE_DAILY_CAP.get(),"Daily XP cap holds while active time continues");
                var idle=TramaRuntime.entry(afk.player);
                h.assertTrue(idle.experience==0 && idle.activeSeconds==0,"Standing AFK produces neither XP nor active time");
                h.succeed();
            } finally { active.close(); afk.close(); }
        });
    }
    private static void house(ServerPlayer p,String name) {
        HouseManager.assign(p.server,p.getUUID(),ResourceLocation.parse("aurorion_ethereal:"+name));
        TramaRuntime.sync(p);
    }
    private static TestPlayer player(GameTestHelper h,String name) {
        var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),name),false);
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        p.server.getPlayerList().placeNewPlayer(connection,p,cookie);
        p.setPos(h.absolutePos(net.minecraft.core.BlockPos.ZERO).getX()+.5,h.absolutePos(net.minecraft.core.BlockPos.ZERO).getY()+1,h.absolutePos(net.minecraft.core.BlockPos.ZERO).getZ()+.5);
        p.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        TramaRuntime.logout(p.getUUID()); // All login listeners have finished in this synchronous harness.
        return new TestPlayer(p,channel);
    }
    private record TestPlayer(ServerPlayer player,EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            HouseManager.clear(player.server,player.getUUID());
            player.server.getPlayerList().remove(player);
            TramaRuntime.logout(player.getUUID()); channel.finishAndReleaseAll();
        }
    }
}
