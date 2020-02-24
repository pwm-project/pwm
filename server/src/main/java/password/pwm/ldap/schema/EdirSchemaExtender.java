/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.ldap.schema;

import com.novell.ldap.client.SchemaParser;
import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class EdirSchemaExtender implements SchemaExtender
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EdirSchemaExtender.class );

    private static final String LDAP_SCHEMA_DN = "cn=schema";
    private static final String LDAP_SCHEMA_ATTR_ATTRS = "attributeTypes";
    private static final String LDAP_SCHEMA_ATTR_CLASSES = "objectClasses";

    private ChaiEntry schemaEntry;

    private final StringBuilder activityLog = new StringBuilder();
    private final Map<String, SchemaDefinition.State> stateMap = new HashMap<>();

    public void init( final ChaiProvider chaiProvider ) throws PwmUnrecoverableException
    {
        try
        {
            schemaEntry = chaiProvider.getEntryFactory().newChaiEntry( LDAP_SCHEMA_DN );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
    }


    @Override
    public SchemaOperationResult extendSchema( )
            throws PwmUnrecoverableException
    {
        activityLog.delete( 0, activityLog.length() );
        execute( false );
        return new SchemaOperationResult( allStatesCorrect(), getActivityLog() );
    }

    @Override
    public SchemaOperationResult checkExistingSchema( ) throws PwmUnrecoverableException
    {
        execute( true );
        return new SchemaOperationResult( allStatesCorrect(), getActivityLog() );
    }

    private boolean allStatesCorrect( )
    {
        boolean allStatesCorrect = true;
        for ( final SchemaDefinition.State value : stateMap.values() )
        {
            if ( SchemaDefinition.State.correct != value )
            {
                allStatesCorrect = false;
            }
        }
        return allStatesCorrect;
    }

    private void execute( final boolean readOnly ) throws PwmUnrecoverableException
    {
        activityLog.delete( 0, activityLog.length() );
        logActivity( "connecting to " + schemaEntry.getChaiProvider().getChaiConfiguration().bindURLsAsList().iterator().next() );
        stateMap.clear();
        try
        {
            final Map<String, SchemaParser> existingAttrs = readSchemaAttributes();
            for ( final SchemaDefinition schemaDefinition : SchemaDefinition.getPwmSchemaDefinitions() )
            {
                if ( schemaDefinition.getSchemaType() == SchemaDefinition.SchemaType.attribute )
                {
                    checkAttribute( readOnly, schemaDefinition, existingAttrs );
                }
            }

            final Map<String, SchemaParser> existingObjectclasses = readSchemaObjectclasses();
            for ( final SchemaDefinition schemaDefinition : SchemaDefinition.getPwmSchemaDefinitions() )
            {
                if ( schemaDefinition.getSchemaType() == SchemaDefinition.SchemaType.objectclass )
                {
                    checkObjectclass( readOnly, schemaDefinition, existingObjectclasses );
                }
            }
        }
        catch ( final ChaiUnavailableException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, e.getMessage() ) );
        }
        catch ( final ChaiOperationException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, e.getMessage() ) );
        }

    }

    private void checkObjectclass( final boolean readOnly, final SchemaDefinition schemaDefinition, final Map<String, SchemaParser> existingAttrs )
            throws ChaiUnavailableException
    {
        final String name = schemaDefinition.getName();

        if ( existingAttrs.containsKey( name ) )
        {
            final SchemaParser existingValue = existingAttrs.get( name );
            logActivity( "objectclass '" + name + "' exists" );
            final boolean objectclassIsCorrect = checkObjectclassCorrectness( schemaDefinition, existingValue );
            stateMap.put( name, objectclassIsCorrect ? SchemaDefinition.State.correct : SchemaDefinition.State.incorrect );

            if ( !readOnly && !objectclassIsCorrect )
            {
                logActivity( "beginning update for objectclass '" + name + "'" );
                try
                {
                    schemaEntry.replaceAttribute( LDAP_SCHEMA_ATTR_CLASSES, existingValue.getRawString(), schemaDefinition.getDefinition() );
                    logActivity( "+ objectclass '" + name + "' has been modified" );
                    stateMap.put( name, SchemaDefinition.State.correct );
                }
                catch ( final ChaiOperationException e )
                {
                    logActivity( "error while updating objectclass definition '" + name + "', error: " + e.getMessage() );
                }
            }
        }
        else
        {
            logActivity( "objectclass '" + name + "' does not exist" );
            stateMap.put( name, SchemaDefinition.State.missing );
            if ( !readOnly )
            {
                logActivity( "beginning add for objectclass '" + name + "'" );
                try
                {
                    schemaEntry.addAttribute( LDAP_SCHEMA_ATTR_CLASSES, schemaDefinition.getDefinition() );
                    logActivity( "+ objectclass '" + name + "' has been added" );
                    stateMap.put( name, SchemaDefinition.State.correct );
                }
                catch ( final ChaiOperationException e )
                {
                    logActivity( "error while updating objectclass definition '" + name + "', error: " + e.getMessage() );
                }
            }
        }
    }

    private void checkAttribute( final boolean readOnly, final SchemaDefinition schemaDefinition, final Map<String, SchemaParser> existingAttrs ) throws ChaiUnavailableException
    {
        final String name = schemaDefinition.getName();
        if ( existingAttrs.containsKey( name ) )
        {
            final SchemaParser existingValue = existingAttrs.get( name );
            logActivity( "attribute '" + name + "' exists" );
            final boolean attributeIsCorrect = checkAttributeCorrectness( schemaDefinition, existingValue );
            stateMap.put( name, attributeIsCorrect ? SchemaDefinition.State.correct : SchemaDefinition.State.incorrect );

            if ( !readOnly && !attributeIsCorrect )
            {
                logActivity( "beginning update for attribute '" + name + "'" );
                try
                {
                    schemaEntry.replaceAttribute( LDAP_SCHEMA_ATTR_ATTRS, existingValue.getRawString(), schemaDefinition.getDefinition() );
                    logActivity( "+ attribute '" + name + "' has been modified" );
                    stateMap.put( name, SchemaDefinition.State.correct );
                }
                catch ( final ChaiOperationException e )
                {
                    logActivity( "error while updating attribute definition '" + name + "', error: " + e.getMessage() );
                }
            }
        }
        else
        {
            logActivity( "attribute '" + name + "' does not exist" );
            if ( !readOnly )
            {
                logActivity( "beginning add for attribute '" + name + "'" );
                try
                {
                    schemaEntry.addAttribute( LDAP_SCHEMA_ATTR_ATTRS, schemaDefinition.getDefinition() );
                    stateMap.put( name, SchemaDefinition.State.missing );
                    logActivity( "+ attribute '" + name + "' has been added" );
                    stateMap.put( name, SchemaDefinition.State.correct );
                }
                catch ( final ChaiOperationException e )
                {
                    logActivity( "error while adding attribute definition '" + name + "', error: " + e.getMessage() );
                }
            }
        }
    }

    private boolean checkObjectclassCorrectness( final SchemaDefinition schemaDefinition, final SchemaParser existingAttr )
    {
        boolean checkPassed = true;
        try
        {
            final SchemaParser schemaDef = new SchemaParser( schemaDefinition.getDefinition() );
            {
                final String defId = schemaDef.getID();
                final String existingID = existingAttr.getID();
                if ( defId != null && !defId.equals( existingID ) )
                {
                    logActivity( "objectclass '" + schemaDefinition.getName()
                            + "' ID (" + existingID + ") is not correct, correct ID is (" + defId + ")" );
                    checkPassed = false;
                }
            }
            {
                final Set<String> defOptionals = new TreeSet<>( Arrays.asList( schemaDef.getOptional() ) );
                final Set<String> existingOptionals = new TreeSet<>( Arrays.asList( existingAttr.getOptional() ) );
                if ( !defOptionals.equals( existingOptionals ) )
                {
                    logActivity( "objectclass '" + schemaDefinition.getName()
                            + "' optional attributes (" + JsonUtil.serializeCollection( defOptionals )
                            + ") is not correct, correct optional attribute list is ("
                            + JsonUtil.serializeCollection( existingOptionals ) + ")" );
                    checkPassed = false;
                }
            }
        }
        catch ( final IOException e )
        {
            e.printStackTrace();
        }

        return checkPassed;
    }

    private boolean checkAttributeCorrectness( final SchemaDefinition schemaDefinition, final SchemaParser existingAttr )
    {
        boolean checkPassed = true;
        try
        {
            final SchemaParser schemaDef = new SchemaParser( schemaDefinition.getDefinition() );
            {
                final String defId = schemaDef.getID();
                final String existingID = existingAttr.getID();
                if ( defId != null && !defId.equals( existingID ) )
                {
                    logActivity( "attribute '" + schemaDefinition.getName() + "' ID (" + existingID + ") is not correct, correct ID is (" + defId + ")" );
                    checkPassed = false;
                }
            }
            {
                final String defSyntax = normalizeSyntaxID( schemaDef.getSyntax() );
                final String existingSyntax = normalizeSyntaxID( existingAttr.getSyntax() );
                if ( defSyntax != null && !defSyntax.equals( existingSyntax ) )
                {
                    logActivity( "attribute '" + schemaDefinition.getName() + "' syntax (" + existingSyntax + ") is not correct, correct syntax is (" + defSyntax + ")" );
                    checkPassed = false;
                }
            }
        }
        catch ( final IOException e )
        {
            e.printStackTrace();
        }

        return checkPassed;
    }

    private Map<String, SchemaParser> readSchemaAttributes( ) throws ChaiUnavailableException, ChaiOperationException
    {
        final Map<String, SchemaParser> returnObj = new LinkedHashMap<>();

        final Set<String> valuesFromLdap = schemaEntry.readMultiStringAttribute( LDAP_SCHEMA_ATTR_ATTRS );
        for ( final String key : valuesFromLdap )
        {
            SchemaParser schemaParser = null;
            try
            {
                schemaParser = new SchemaParser( key );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error parsing schema attribute definition: " + e.getMessage() );
            }
            if ( schemaParser != null )
            {
                for ( final String attrName : schemaParser.getNames() )
                {
                    returnObj.put( attrName, schemaParser );
                }
            }
        }
        return returnObj;
    }

    private Map<String, SchemaParser> readSchemaObjectclasses( ) throws ChaiUnavailableException, ChaiOperationException
    {
        final Map<String, SchemaParser> returnObj = new LinkedHashMap<>();

        final Set<String> valuesFromLdap = schemaEntry.readMultiStringAttribute( LDAP_SCHEMA_ATTR_CLASSES );
        for ( final String key : valuesFromLdap )
        {
            SchemaParser schemaParser = null;
            try
            {
                schemaParser = new SchemaParser( key );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error parsing schema objectclasses definition: " + e.getMessage() );
            }
            if ( schemaParser != null )
            {
                for ( final String attrName : schemaParser.getNames() )
                {
                    returnObj.put( attrName, schemaParser );
                }
            }
        }
        return returnObj;
    }

    private void logActivity( final CharSequence charSequence )
    {
        LOGGER.info( () -> charSequence );
        activityLog.append( charSequence ).append( "\n" );
    }

    private String getActivityLog( )
    {
        return activityLog.toString();
    }

    private String normalizeSyntaxID( final String input )
    {
        return input == null ? "" : input.replaceFirst( "\\{[0-9]+\\}$", "" );
    }

}
