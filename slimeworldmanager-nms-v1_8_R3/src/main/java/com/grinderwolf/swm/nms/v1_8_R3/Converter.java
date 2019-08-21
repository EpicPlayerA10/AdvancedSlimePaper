package com.grinderwolf.swm.nms.v1_8_R3;

import com.flowpowered.nbt.*;
import com.grinderwolf.swm.api.world.SlimeChunk;
import com.grinderwolf.swm.api.world.SlimeChunkSection;
import com.grinderwolf.swm.api.utils.NibbleArray;
import com.grinderwolf.swm.nms.CraftSlimeChunk;
import com.grinderwolf.swm.nms.CraftSlimeChunkSection;
import net.minecraft.server.v1_8_R3.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Converter {

    public static net.minecraft.server.v1_8_R3.NibbleArray convertArray(NibbleArray array) {
        return new net.minecraft.server.v1_8_R3.NibbleArray(array.getBacking());
    }

    public static NibbleArray convertArray(net.minecraft.server.v1_8_R3.NibbleArray array) {
        return new NibbleArray(array.a());
    }

    public static NBTBase convertTag(Tag tag) {
        switch (tag.getType()) {
            case TAG_BYTE:
                return new NBTTagByte(((ByteTag) tag).getValue());
            case TAG_SHORT:
                return new NBTTagShort(((ShortTag) tag).getValue());
            case TAG_INT:
                return new NBTTagInt(((IntTag) tag).getValue());
            case TAG_LONG:
                return new NBTTagLong(((LongTag) tag).getValue());
            case TAG_FLOAT:
                return new NBTTagFloat(((FloatTag) tag).getValue());
            case TAG_DOUBLE:
                return new NBTTagDouble(((DoubleTag) tag).getValue());
            case TAG_BYTE_ARRAY:
                return new NBTTagByteArray(((ByteArrayTag) tag).getValue());
            case TAG_STRING:
                return new NBTTagString(((StringTag) tag).getValue());
            case TAG_INT_ARRAY:
                return new NBTTagIntArray(((IntArrayTag) tag).getValue());
            case TAG_LIST:
                NBTTagList list = new NBTTagList();

                //noinspection unchecked
                ((ListTag<?>) tag).getValue().stream().map(Converter::convertTag).forEach(list::add);

                return list;
            case TAG_COMPOUND:
                NBTTagCompound compound = new NBTTagCompound();

                ((CompoundTag) tag).getValue().forEach((key, value) -> compound.set(key, convertTag(value)));

                return compound;
            default:
                throw new IllegalArgumentException("Invalid tag type " + tag.getType().name());
        }
    }

    private static Tag convertTag(String name, NBTBase base) {
        switch (base.getTypeId()) {
            case 1:
                return new ByteTag(name, ((NBTTagByte) base).f());
            case 2:
                return new ShortTag(name, ((NBTBase.NBTNumber) base).e());
            case 3:
                return new IntTag(name, ((NBTBase.NBTNumber) base).d());
            case 4:
                return new LongTag(name, ((NBTBase.NBTNumber) base).c());
            case 5:
                return new FloatTag(name, ((NBTBase.NBTNumber) base).h());
            case 6:
                return new DoubleTag(name, ((NBTBase.NBTNumber) base).g());
            case 7:
                return new ByteArrayTag(name, ((NBTTagByteArray) base).c());
            case 8:
                return new StringTag(name, ((NBTTagString) base).a_());
            case 9:
                List<Tag> list = new ArrayList<>();
                NBTTagList originalList = ((NBTTagList) base);

                for (int i = 0; i < originalList.size(); i++) {
                    NBTBase entry = originalList.g(i);
                    list.add(convertTag("", entry));
                }

                return new ListTag(name, TagType.getById(originalList.f()), list);
            case 10:
                NBTTagCompound originalCompound = ((NBTTagCompound) base);
                CompoundTag compound = new CompoundTag(name, new CompoundMap());

                for (String key : originalCompound.c()) {
                    compound.getValue().put(key, convertTag(key, originalCompound.get(key)));
                }

                return compound;
            case 11:
                return new IntArrayTag("", ((NBTTagIntArray) base).c());
            default:
                throw new IllegalArgumentException("Invalid tag type " + base.getTypeId());
        }
    }

    public static SlimeChunk convertChunk(Chunk chunk) {
        // Chunk sections
        SlimeChunkSection[] sections = new SlimeChunkSection[16];

        for (int sectionId = 0; sectionId < chunk.getSections().length; sectionId++) {
            ChunkSection section = chunk.getSections()[sectionId];

            if (section != null) {
                section.recalcBlockCounts();

                if (!section.a()) { // If the section is empty, just ignore it to save space
                    // Block Light Nibble Array
                    NibbleArray blockLightArray = convertArray(section.getEmittedLightArray());

                    // Sky light Nibble Array
                    NibbleArray skyLightArray = convertArray(section.getSkyLightArray());

                    // Block Data
                    byte[] blocks = new byte[4096];
                    NibbleArray blockDataArray = new NibbleArray(4096);

                    for (int i = 0; i < section.getIdArray().length; i++) {
                        char packed = section.getIdArray()[i];

                        blocks[i] = (byte) (packed >> 4 & 255);
                        blockDataArray.set(i, packed & 15);
                    }

                    sections[sectionId] = new CraftSlimeChunkSection(blocks, blockDataArray, null, null, blockLightArray, skyLightArray);
                }
            }
        }

        // Tile Entities
        ArrayList<CompoundTag> tileEntities = new ArrayList<>();

        for (TileEntity entity : chunk.getTileEntities().values()) {
            NBTTagCompound entityNbt = new NBTTagCompound();
            entity.b(entityNbt);
            tileEntities.add((CompoundTag) convertTag("", entityNbt));
        }

        // Entities
        ArrayList<CompoundTag> entities = new ArrayList<>();

        for (int i = 0; i < chunk.getEntitySlices().length; i++) {
            for (Entity entity : chunk.getEntitySlices()[i]) {
                NBTTagCompound entityNbt = new NBTTagCompound();

                if (entity.d(entityNbt)) {
                    chunk.g(true);
                    entities.add((CompoundTag) convertTag("", entityNbt));
                }
            }
        }

        // Biomes
        byte[] byteBiomes = chunk.getBiomeIndex();
        int[] biomes = toIntArray(byteBiomes);

        // HeightMap
        CompoundTag heightMapsCompound = new CompoundTag("", new CompoundMap());
        heightMapsCompound.getValue().put("heightMap", new IntArrayTag("heightMap", chunk.heightMap));

        return new CraftSlimeChunk(chunk.world.worldData.getName(), chunk.locX, chunk.locZ, sections, heightMapsCompound, biomes, tileEntities, entities);
    }

    private static int[] toIntArray(byte[] buf) {
        ByteBuffer buffer = ByteBuffer.wrap(buf).order(ByteOrder.BIG_ENDIAN);
        int[] ret = new int[buf.length / 4];

        buffer.asIntBuffer().get(ret);

        return ret;
    }
}