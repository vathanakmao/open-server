package com.wadpam.open.service;

import com.google.appengine.api.NamespaceManager;
import com.wadpam.open.dao.DEmployeeDao;
import com.wadpam.open.dao.DOrganizationDao;
import com.wadpam.open.domain.DEmployee;
import com.wadpam.open.domain.DOrganization;
import com.wadpam.open.io.Converter;
import com.wadpam.open.io.CsvConverter;
import com.wadpam.open.io.ExcelConverter;
import com.wadpam.open.io.Exporter;
import com.wadpam.open.io.Extractor;
import com.wadpam.open.io.JsonConverter;
import com.wadpam.open.io.Mardao2Extractor;
import java.io.OutputStream;
import java.util.Arrays;
import net.sf.mardao.core.dao.Dao;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 * @author os
 */
public class ExportService {
    static final Converter<Dao> CONVERTER_CSV = new CsvConverter<Dao>();
    static final Converter<Dao> CONVERTER_EXCEL = new ExcelConverter<Dao>();
    static final Converter<Dao> CONVERTER_JSON = new JsonConverter<Dao>();
    
    static final Extractor<Dao> EXTRACTOR_MARDAO = new Mardao2Extractor();
    
    @Autowired
    private DEmployeeDao employeeDao;
    
    @Autowired
    private DOrganizationDao organizationDao;
    
    private Exporter<Dao> exporter;

    public void init() {
        exporter = new Exporter<Dao>(new Dao[] {organizationDao, employeeDao});        
        
        final String currentNamespace = NamespaceManager.get();
        NamespaceManager.set("itest");
        exporter.setExtractor(EXTRACTOR_MARDAO);
        DOrganization wadpam = organizationDao.persist(null, "Wadpam AB");
        DOrganization bassac = organizationDao.persist(null, "bassac.se");
        
        DEmployee ola = employeeDao.persist(null, "Ola", null, wadpam);
        DEmployee jan = employeeDao.persist(null, "Jan", ola, wadpam);
        
        DEmployee mattias = employeeDao.persist(null, "Mattias", null, bassac);
        DEmployee erik = employeeDao.persist(null, "Erik", mattias, bassac);
        NamespaceManager.set(currentNamespace);
    }
    
    public void exportAll(OutputStream out, String contentType) {
        exporter.setConverter("application/json".equals(contentType) ? 
                CONVERTER_JSON : CONVERTER_EXCEL);
        exporter.export(out, this);
    }

    public void exportDao(OutputStream out, String tableName) {
        Dao dao = "employees".equals(tableName) ? employeeDao : organizationDao;
        
        exporter.setConverter(CONVERTER_CSV);
        exporter.export(out, this, dao);
    }
}
