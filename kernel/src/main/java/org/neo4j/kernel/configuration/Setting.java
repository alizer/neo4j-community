/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.neo4j.graphdb.config.InvalidConfigurationValueException;
import org.neo4j.helpers.Function;
import org.neo4j.helpers.Predicate;

import static java.util.Collections.addAll;
import static java.util.Collections.emptyList;

public abstract class Setting<T> implements org.neo4j.graphdb.config.Setting
{
    public static Setting<String> stringSetting( String name )
    {
        return stringSetting( name, Setting.<String>noDefaultValue() );
    }

    public static Setting<String> stringSetting( String name, DefaultValue<String> defaultValue )
    {
        return new Setting<String>( name, defaultValue )
        {
            @Override
            String parse( String value, Locale locale, Config config )
            {
                return value;
            }
        };
    }

    public static Setting<Long> integerSetting( String name )
    {
        return integerSetting( name, Setting.<Long>noDefaultValue() );
    }

    public static Setting<Long> integerSetting( String name, DefaultValue<Long> defaultValue )
    {
        return new Setting<Long>( name, defaultValue )
        {
            @Override
            Long parse( String value, Locale locale, Config config )
            {
                return Long.parseLong( value );
            }
        };
    }

    public static Setting<Double> floatingPointSetting( String name )
    {
        return floatingPointSetting( name, Setting.<Double>noDefaultValue() );
    }

    public static Setting<Double> floatingPointSetting( String name, DefaultValue<Double> defaultValue )
    {
        return new Setting<Double>( name, defaultValue )
        {
            @Override
            Double parse( String value, Locale locale, Config config )
            {
                return Double.parseDouble( value );
            }
        };
    }

    public static Setting<Boolean> booleanSetting( String name )
    {
        return booleanSetting( name, Setting.<Boolean>noDefaultValue() );
    }

    public static Setting<Boolean> booleanSetting( String name, DefaultValue<Boolean> defaultValue )
    {
        return new Setting<Boolean>( name, defaultValue )
        {
            @Override
            Boolean parse( String value, Locale locale, Config config )
            {
                return Boolean.parseBoolean( value );
            }

            @Override
            boolean isBoolean()
            {
                return true;
            }
        };
    }

    public static <T extends Enum<T>> Setting<T> enumSetting( Class<T> enumType, String name )
    {
        return enumSetting( enumType, name, Setting.<T>noDefaultValue() );
    }

    private static <T extends Enum<T>> Setting<T> enumSetting( final Class<T> enumType, String name,
                                                               DefaultValue<T> defaultValue )
    {
        return new Setting<T>( name, defaultValue )
        {
            @Override
            T parse( String value, Locale locale, Config config )
            {
                return Enum.valueOf( enumType, value );
            }

            @Override
            String asString( T value, Locale locale )
            {
                return value.name();
            }
        };
    }

    public static <T> Setting<List<T>> listSetting( Setting<T> singleSetting )
    {
        return listSetting( singleSetting, ",", Setting.<List<T>>noDefaultValue() );
    }

    public static <T> Setting<List<T>> listSetting( Setting<T> singleSetting, String separator )
    {
        return listSetting( singleSetting, separator, Setting.<List<T>>noDefaultValue() );
    }

    public static <T> Setting<List<T>> listSetting( Setting<T> singleSetting, DefaultValue<List<T>> defaultValue )
    {
        return listSetting( singleSetting, ",", defaultValue );
    }

    public static <T> Setting<List<T>> listSetting( final Setting<T> singleSetting, final String separator,
                                                    DefaultValue<List<T>> defaultValue )
    {
        return new Setting<List<T>>( singleSetting.name(), defaultValue )
        {
            @Override
            List<T> parse( String value, Locale locale, Config config )
            {
                if ( value.trim().equals( "" ) )
                {
                    return emptyList();
                }
                String[] parts = value.split( separator );
                List<T> result = new ArrayList<T>( parts.length );
                for ( String part : parts )
                {
                    result.add( singleSetting.parse( part, locale, config ) );
                }
                return result;
            }

            @Override
            public void validate( Locale locale, String value ) throws InvalidConfigurationValueException
            {
                if ( value.trim().equals( "" ) )
                {
                    return; // ok - empty list
                }
                String[] parts = value.split( separator );
                for ( String part : parts )
                {
                    singleSetting.validate( locale, part );
                }
            }

            @Override
            String asString( List<T> value, Locale locale )
            {
                StringBuilder result = new StringBuilder();
                Iterator<T> iterator = value.iterator();
                while ( iterator.hasNext() )
                {
                    result.append( singleSetting.asString( iterator.next(), locale ) );
                    if ( iterator.hasNext() )
                    {
                        result.append( separator );
                    }
                }
                return result.toString();
            }
        };
    }

    public static <T> Setting<T> restrictSetting( Setting<T> setting, Predicate<? super T> firstPredicate,
                                                  Predicate<? super T>... morePredicates )
    {
        final Collection<Predicate<? super T>> predicates = new ArrayList<Predicate<? super T>>(
                1 + (morePredicates == null ? 0 : morePredicates.length) );
        predicates.add( firstPredicate );
        if ( morePredicates != null )
        {
            addAll( predicates, morePredicates );
        }
        return new SettingAdapter<T, T>( setting, Setting.<T>noDefaultValue() )
        {
            @Override
            T adapt( T value, Config config )
            {
                for ( Predicate<? super T> predicate : predicates )
                {
                    if ( !predicate.accept( value ) )
                    {
                        throw new IllegalArgumentException(
                                String.format( "'%s' does not match %s", value, predicate ) );
                    }
                }
                return value;
            }

            @Override
            String asString( T value, Locale locale )
            {
                return source.asString( value, locale );
            }
        };
    }

    public static <FROM, TO> Setting<TO> adaptSetting( Setting<FROM> source,
                                                       final Function<? super FROM, TO> conversion )
    {
        return adaptSetting( source, conversion, Setting.<TO>noDefaultValue() );
    }

    public static <FROM, TO> SettingAdapter<FROM, TO> adaptSetting( Setting<FROM> source,
                                                                    final Function<? super FROM, TO> conversion,
                                                                    DefaultValue<? extends TO> defaultValue )
    {
        return new SettingAdapter<FROM, TO>( source, defaultValue )
        {
            @Override
            TO adapt( FROM value, Config config )
            {
                return conversion.map( value );
            }
        };
    }

    public static abstract class DefaultValue<T>
    {
        protected abstract T defaultValue( Setting<? super T> setting, Config config )
                throws NoDefaultConfigurationException;

        private static final DefaultValue<?> NONE = new DefaultValue<Object>()
        {
            @Override
            protected Object defaultValue( Setting<? super Object> setting, Config config )
                    throws NoDefaultConfigurationException
            {
                throw new NoDefaultConfigurationException( setting );
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> DefaultValue<T> noDefaultValue()
    {
        return (DefaultValue<T>) DefaultValue.NONE;
    }

    private static <T> DefaultValue<T> defaultValue( final T defaultValue )
    {
        return new DefaultValue<T>()
        {
            @Override
            protected T defaultValue( Setting<? super T> setting, Config config ) throws NoDefaultConfigurationException
            {
                return defaultValue;
            }
        };
    }

    private final String name;
    final DefaultValue<? extends T> defaultValue;

    private Setting( String name, DefaultValue<? extends T> defaultValue )
    {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    @Override
    public String toString()
    {
        return String.format( "Setting[%s]", name );
    }

    @Override
    public String name()
    {
        return null;
    }

    @Override
    public void validate( Locale locale, String value ) throws InvalidConfigurationValueException
    {
        try
        {
            parse( value, locale, null );
        }
        catch ( Exception cause )
        {
            throw new InvalidConfigurationValueException( this, value, cause );
        }
    }

    abstract T parse( String value, Locale locale, Config config );

    boolean isBoolean()
    {
        return false;
    }

    T getDefaultValue( Config config ) throws NoDefaultConfigurationException
    {
        return defaultValue.defaultValue( this, config );
    }

    String asString( T value, Locale locale )
    {
        return value.toString();
    }

    private static abstract class SettingAdapter<FROM, TO> extends Setting<TO>
    {
        final Setting<FROM> source;

        SettingAdapter( Setting<FROM> source, DefaultValue<? extends TO> defaultValue )
        {
            super( source.name, defaultValue );
            this.source = source;
        }

        @Override
        protected TO parse( String value, Locale locale, Config config )
        {
            return adapt( source.parse( value, locale, config ), config );
        }

        abstract TO adapt( FROM value, Config config );

        @Override
        TO getDefaultValue( Config config ) throws NoDefaultConfigurationException
        {
            if ( this.defaultValue == DefaultValue.NONE )
            {
                return super.getDefaultValue( config );
            }
            else
            {
                return adapt( source.getDefaultValue( config ), config );
            }
        }
    }
}
