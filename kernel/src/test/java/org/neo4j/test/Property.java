package org.neo4j.test;

import org.neo4j.graphdb.PropertyContainer;

public final class Property
{
    public static Property property( String key, Object value )
    {
        return new Property( key, value );
    }

    public static <E extends PropertyContainer> E set( E entity, Property... properties )
    {
        for ( Property property : properties )
        {
            entity.setProperty( property.key, property.value );
        }
        return entity;
    }

    private final String key;
    private final Object value;

    private Property( String key, Object value )
    {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString()
    {
        return String.format( "%s: %s", key, value );
    }
}
