/**
 * Copyright (C) 2014 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
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
package org.holodeckb2b.ebms3.persistent.message;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import org.holodeckb2b.ebms3.persistent.general.Description;
import org.holodeckb2b.ebms3.persistent.general.Property;
import org.holodeckb2b.ebms3.persistent.general.SchemaReference;
import org.holodeckb2b.interfaces.general.IDescription;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.general.ISchemaReference;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.IPayload.Containment;

/**
 * Is persistency class representing a <i>Payload</i> of an ebMS User Message. 
 * These payloads contain the actual business data which are transferred between
 * business applications, i.e. the Holodeck B2B client apps. 
 * 
 * <p>The data itself is not relevant to Holodeck B2B and not used in processing 
 * the payload. Therefor only the meta data is stored in the Holodeck B2B database.
 * The content of the payload is written to disk. This is also done because the
 * payload size is unknown and unlimited.
 * 
 * @author Sander Fieten <sander at holodeckb2b.org>
 */
@Entity
@Table(name="PAYLOAD")
public class Payload implements Serializable, IPayload {

    /*
     * Getters and setters
     */
    @Override
    public Containment getContainment() {
        return CONTAINMENT;
    }
    
    public void setContainment(Containment containment) {
        CONTAINMENT = containment;
    }
    
    @Override
    public String getPayloadURI() {
        return URI;
    }
    
    public void setPayloadURI(String payloadURI) {
        URI = payloadURI;
    }

    @Override
    public Collection<IProperty> getProperties() {
        return properties;
    }
    
    public void setProperties(Collection<Property> props) {
        if (properties == null) 
            properties = new ArrayList<IProperty>();
                    
        if ( props == null || props.isEmpty())
            properties.clear();
        else 
            properties.addAll(props);
    }

    public void addProperty(Property p) {
        if( properties == null)
            properties = new ArrayList<IProperty>();
        properties.add(p);
    }

    @Override
    public IDescription getDescription() {
        return description;
    }

    public void setDescription(Description descr) {
        description = descr;
    }
    
    @Override
    public ISchemaReference getSchemaReference() {
        return schemaRef;
    }    

    public void setSchemaReference(SchemaReference schemaRef) {
        this.schemaRef = schemaRef;
    }
    
    @Override
    public String getContentLocation() {
        return FILE_LOCATION;
    }
    
    public void setContentLocation(String location) {
        FILE_LOCATION = location;
    }
    
    @Override
    public String getMimeType() {
        return MIME_TYPE;
    }
    
    public void setMimeType(String mimeType) {
        MIME_TYPE = mimeType;
    }
    
    /*
     * Constructors
     */
    public Payload() {}
    
    /**
     * Creates a simple Payload object with only the essential information as
     * content location and mime type. 
     * <p>When sending a message this will result in SOAP attachment with a 
     * mime Content-id generated by Holodeck B2B.
     */
    public Payload(String location, String mimeType) {
        this();
        
        this.FILE_LOCATION = location;
        this.MIME_TYPE = mimeType;
    }
    
    /**
     * Creates a simple Payload object for the data at the given location with
     * the specified mime type and URI. 
     */
    public Payload(String location, String mimeType, String URI) {
        this(location, mimeType);
        
        this.URI = URI;
    }
    
    /*
     * Fields
     * 
     * NOTE: The JPA @Column annotation is not used so the attribute names are 
     * used as column names. Therefor the attribute names are in CAPITAL.
     */
    
    /*
     * Technical object id acting as the primary key
     */
    @Id
    @GeneratedValue
    private long    OID;

    @ElementCollection(targetClass = Property.class)
    @CollectionTable(name="PL_PROPERTIES")
    private Collection<IProperty>   properties;
    
    @Embedded
    private Description         description;
    
    @Embedded
    private SchemaReference     schemaRef;
    
    private String              URI;
    
    private String              FILE_LOCATION;
    
    private String              MIME_TYPE;

    @Enumerated(EnumType.STRING)
    private Containment         CONTAINMENT;
}
