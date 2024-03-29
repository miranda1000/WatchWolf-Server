package dev.watchwolf.server;

import dev.watchwolf.entities.*;
import dev.watchwolf.entities.blocks.Block;
import dev.watchwolf.entities.blocks.BlockReader;
import dev.watchwolf.entities.entities.Entity;
import dev.watchwolf.entities.entities.EntityType;
import dev.watchwolf.entities.files.ConfigFile;
import dev.watchwolf.entities.items.Item;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServerConnector implements Runnable {
    public interface ArrayAdder { public void addToArray(ArrayList<Byte> out, Object []file); }

    /**
     * The only IP allowed to talk to the socket
     */
    private final String allowedIp;

    /**
     * Socket to receive <allowedIp>'s requests
     */
    private final ServerSocket serverSocket;

    /**
     * Client connected to <serverSocket>
     */
    private Socket clientSocket;

    /**
     * Needed to run sync operations
     */
    private final SequentialExecutor executor;

    /**
     * Implementations of the server petitions
     */
    private final ServerPetition serverPetition;

    public ServerConnector(String allowedIp, int port, SequentialExecutor executor, ServerPetition serverPetition) throws IOException {
        this.allowedIp = allowedIp;
        this.serverSocket = new ServerSocket(port);
        this.executor = executor;
        this.serverPetition = serverPetition;

        SocketData.loadStaticBlock(BlockReader.class);
        SocketData.loadStaticBlock(EntityType.class);
    }

    public void close() {
        try {
            this.serverSocket.close();
            if (this.clientSocket != null) this.clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* server socket */

    @Override
    public void run() {
        while (!this.serverSocket.isClosed()) {
            try {
                this.clientSocket = this.serverSocket.accept();
                System.out.println(this.clientSocket.getInetAddress().getHostAddress() + " - " + this.allowedIp); // TODO deny connection
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (this.clientSocket != null && !this.clientSocket.isClosed()) {
                try {
                    DataInputStream dis = new DataInputStream(this.clientSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(this.clientSocket.getOutputStream());

                    int first = dis.readUnsignedByte();
                    if ((first & 0b1111) != 0b0001) throw new UnexpectedPacketException("The packet must end with '0_001', found " + Integer.toBinaryString(first & 0b1111) + " (" + Integer.toBinaryString(first) + ")");
                    int group = ((dis.readUnsignedByte() << 4) | (first >> 4));
                    this.processGroup(group, dis, dos);
                } catch (EOFException ignore) {
                    break; // socket closed
                } catch (IOException ex) {
                    ex.printStackTrace();
                } catch (UnexpectedPacketException ex) {
                    ex.printStackTrace();
                }
            }

            if (this.clientSocket != null && !this.clientSocket.isClosed()) {
                try {
                    this.clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void processGroup(int group, DataInputStream dis, DataOutputStream dos) throws IOException, UnexpectedPacketException {
        switch (group) {
            case 0: // NOP
                // TODO extend timeout
                break;

            case 1:
                this.processDefaultGroup(dis, dos);
                break;

            case 2:
                this.processEIGroup(dis, dos);
                break;

            case 3:
                this.processWorldGuardGroup(dis, dos);
                break;

            default:
                // TODO send 'unimplemented'
                throw new UnexpectedPacketException("Unimplemented group: " + group);
        }
    }

    private void processDefaultGroup(DataInputStream dis, DataOutputStream dos) throws IOException, UnexpectedPacketException {
        String nick, cmd, uuid;
        Position position;
        Block block;
        Item item;
        Entity entity;
        Difficulty difficulty;
        int operation = SocketHelper.readShort(dis);
        double radius;
        boolean state;
        switch (operation) {
            case 0x0001:
                this.executor.run(() -> this.serverPetition.stopServer(null));
                break;

            case 0x0003:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> this.serverPetition.whitelistPlayer(nick));
                break;

            case 0x0004:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> this.serverPetition.opPlayer(nick));
                break;

            case 0x0005:
                position = (Position) SocketData.readSocketData(dis, Position.class);
                block = (Block) SocketData.readSocketData(dis, Block.class);
                this.executor.run(() -> this.serverPetition.setBlock(position, block));
                break;

            case 0x0006:
                position = (Position) SocketData.readSocketData(dis, Position.class);
                this.executor.run(() -> {
                    Block b = this.serverPetition.getBlock(position);
                    Message msg = new Message(dos);

                    // get block response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0006);

                    msg.add(b);

                    msg.send();
                });
                break;

            case 0x0007:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    Position playerPosition = this.serverPetition.getPlayerPosition(nick);
                    Message msg = new Message(dos);

                    // get block response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0007);

                    msg.add(playerPosition);

                    msg.send();
                });
                break;

            case 0x0008:
                nick = SocketHelper.readString(dis);
                item = (Item)SocketData.readSocketData(dis, Item.class);
                this.executor.run(() -> this.serverPetition.giveItem(nick, item));
                break;

            case 0x0009:
                cmd = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    String response = this.serverPetition.runCommand(cmd);
                    Message msg = new Message(dos);

                    // run cmd response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0009);

                    msg.add(response);

                    msg.send();
                });
                break;

            case 0x000A:
                this.executor.run(() -> {
                    String []users = this.serverPetition.getPlayers();
                    Message msg = new Message(dos);

                    // get block response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x000A);

                    msg.add((short) users.length);
                    for (int n = 0; n < users.length; n++) msg.add(users[n]);

                    msg.send();
                });
                break;

            case 0x000B:
                this.executor.run(() -> {
                    this.serverPetition.synchronize();
                    Message msg = new Message(dos);

                    // get block response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x000B);

                    msg.send();
                });
                break;

            case 0x000C:
                nick = SocketHelper.readString(dis);
                position = (Position) SocketData.readSocketData(dis, Position.class);
                this.executor.run(() -> this.serverPetition.tp(nick, position));
                break;

            case 0x000D:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    float pitch = this.serverPetition.getPlayerPitch(nick);
                    Message msg = new Message(dos);

                    // get player pitch response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x000D);

                    msg.add(pitch);

                    msg.send();
                });
                break;

            case 0x000E:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    float yaw = this.serverPetition.getPlayerYaw(nick);
                    Message msg = new Message(dos);

                    // get player yaw response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x000E);

                    msg.add(yaw);

                    msg.send();
                });
                break;

            case 0x000F:
                nick = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    Container inv = this.serverPetition.getInventory(nick);
                    Message msg = new Message(dos);

                    // get player yaw response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x000F);

                    msg.add(inv);

                    msg.send();
                });
                break;

            case 0x0010:
                position = (Position) SocketData.readSocketData(dis, Position.class);
                radius = SocketHelper.readDouble(dis);
                this.executor.run(() -> {
                    Entity []entities = this.serverPetition.getEntities(position, radius);
                    Message msg = new Message(dos);

                    // get player yaw response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0010);

                    msg.add((short) entities.length); // TODO move to helper
                    for (int n = 0; n < entities.length; n++) msg.add(entities[n]);

                    msg.send();
                });
                break;

            case 0x0011:
                entity = (Entity) Entity.readSocketData(dis, Entity.class);
                this.executor.run(() -> {
                    Entity e = this.serverPetition.spawnEntity(entity);
                    Message msg = new Message(dos);

                    // get entities response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0011);

                    msg.add(e);

                    msg.send();
                });
                break;

            case 0x0012:
                uuid = SocketHelper.readString(dis);
                this.executor.run(() -> {
                    Entity e = this.serverPetition.getEntity(uuid);
                    Message msg = new Message(dos);

                    // get entity response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0012);

                    msg.add(e);

                    msg.send();
                });
                break;

            case 0x0013:
                difficulty = Difficulty.fromSocketData(dis);
                this.executor.run(() -> this.serverPetition.setDifficulty(difficulty));
                break;

            case 0x0014:
                state = SocketHelper.readBool(dis);
                this.executor.run(() -> this.serverPetition.setInvincibleMode(state));
                break;

            case 0xFFFF:
                this.executor.run(() -> {
                    String version = this.serverPetition.getVersion();
                    Message msg = new Message(dos);

                    // get entity response header
                    msg.add((byte) 0b0001_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0xFFFF);

                    msg.add(version);

                    msg.send();
                });
                break;

            default:
                throw new UnexpectedPacketException("Operation " + (int)operation + " from group 1"); // unimplemented by this version, or error
        }
    }


    private void processEIGroup(DataInputStream dis, DataOutputStream dos) throws IOException, UnexpectedPacketException {
        int operation = SocketHelper.readShort(dis);
        switch (operation) {
            case 0x0004:
                this.executor.run(() -> this.serverPetition.startTimings());
                break;

            case 0x0005:
                this.executor.run(() -> {
                    // we can't run `stopTimings` in the same thread as the server
                    new Thread(()-> {
                        try {
                            ConfigFile timingsReport = this.serverPetition.stopTimings();
                            Message msg = new Message(dos);

                            // stop timings response header
                            msg.add((byte) 0b0010_1_001);
                            msg.add((byte) 0b00000000);
                            msg.add((short) 0x0005);

                            msg.add(timingsReport);

                            msg.send();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).start();
                });
                break;

            default:
                throw new UnexpectedPacketException("Operation " + (int) operation + " from group 2"); // unimplemented by this version, or error
        }
    }


    private void processWorldGuardGroup(DataInputStream dis, DataOutputStream dos) throws IOException, UnexpectedPacketException {
        String name;
        Position pos1, pos2;

        int operation = SocketHelper.readShort(dis);
        switch (operation) {
            case 0x0001:
                name = SocketHelper.readString(dis);
                pos1 = (Position) SocketData.readSocketData(dis, Position.class);
                pos2 = (Position) SocketData.readSocketData(dis, Position.class);

                this.executor.run(() -> this.serverPetition.createRegion(name, pos1, pos2));
                break;

            case 0x0002:
                this.executor.run(() -> {
                    String []regions = this.serverPetition.getRegions();
                    Message msg = new Message(dos);

                    // get region response header
                    msg.add((byte) 0b0011_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0002);

                    // TODO move to helper
                    msg.add((short) regions.length);
                    for (String region : regions) msg.add(region);

                    msg.send();
                });
                break;

            case 0x0003:
                pos1 = (Position) SocketData.readSocketData(dis, Position.class);

                this.executor.run(() -> {
                    String []regions = this.serverPetition.getRegions(pos1);
                    Message msg = new Message(dos);

                    // get region at block response header
                    msg.add((byte) 0b0011_1_001);
                    msg.add((byte) 0b00000000);
                    msg.add((short) 0x0003);

                    // TODO move to helper
                    msg.add((short) regions.length);
                    for (String region : regions) msg.add(region);

                    msg.send();
                });
                break;

            default:
                throw new UnexpectedPacketException("Operation " + (int)operation + " from group 3"); // unimplemented by this version, or error
        }
    }
}
