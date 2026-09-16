package com.aurorion.profissoes;

import com.aurorion.profissoes.data.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ProfessionDataTest {
    @Test void exactlyOneProfessionSurvivesRoundTripAndReset() {
        var data = new ProfessionData(); var account = UUID.randomUUID();
        data.assign(account, Profession.DOCTOR); data.assign(account, Profession.SMITH);
        var loaded = ProfessionData.load(data.save(new CompoundTag(), null), null);
        assertEquals(Profession.SMITH, loaded.of(account));
        loaded.assign(account, Profession.NONE);
        assertEquals(Profession.NONE, ProfessionData.load(loaded.save(new CompoundTag(), null), null).of(account));
    }
    @Test void unreadableDataCannotBeReplacedByAnAssignmentOrReset() {
        var broken = new CompoundTag(); broken.putInt("Version", 99); broken.putString("Future", "preservar");
        var data = ProfessionData.load(broken, null);
        assertThrows(IllegalStateException.class, () -> data.assign(UUID.randomUUID(), Profession.DOCTOR));
        assertThrows(IllegalStateException.class, () -> data.assign(UUID.randomUUID(), Profession.NONE));
        assertEquals(broken, data.save(new CompoundTag(), null));
    }
    @Test void changingOneCharacterDoesNotChangeAnother() {
        var data = new ProfessionData(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        data.assign(a, Profession.ARCANIST); data.assign(b, Profession.CHEF); data.assign(a, Profession.NONE);
        assertEquals(Profession.CHEF, data.of(b));
        assertEquals(Profession.NONE, data.of(a));
    }
    @Test void malformedListIsPreservedInsteadOfBecomingAnEmptyRoster() {
        var broken = new CompoundTag(); broken.putInt("Version", 1);
        var list = new net.minecraft.nbt.ListTag(); list.add(net.minecraft.nbt.StringTag.valueOf("invalido"));
        broken.put("Professions", list);
        var data = ProfessionData.load(broken, null);
        assertThrows(IllegalStateException.class, () -> data.assign(UUID.randomUUID(), Profession.CHEF));
        assertEquals(broken, data.save(new CompoundTag(), null));
    }
}
