package dev.watchwolf.server.worldguard;

import dev.watchwolf.core.entities.Position;
import dev.watchwolf.server.WorldGuardServerPetition;

import java.io.IOException;

public class UnimplementedWorldGuardManager implements WorldGuardServerPetition {
    @Override
    public void createRegion(String s, Position position, Position position1) throws IOException {
        throw new UnsupportedOperationException("WorldGuard manager is not initialized");
    }

    @Override
    public String[] getRegions() throws IOException {
        throw new UnsupportedOperationException("WorldGuard manager is not initialized");
    }

    @Override
    public String[] getRegions(Position position) throws IOException {
        throw new UnsupportedOperationException("WorldGuard manager is not initialized");
    }
}
