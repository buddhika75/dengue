package lk.gov.health.beans;

import com.sun.javafx.scene.control.skin.VirtualFlow;
import lk.gov.health.dengue.Notification;
import lk.gov.health.beans.util.JsfUtil;
import lk.gov.health.beans.util.JsfUtil.PersistAction;
import lk.gov.health.faces.NotificationFacade;

import java.io.Serializable;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.inject.Named;
import javax.enterprise.context.SessionScoped;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.FacesConverter;
import org.primefaces.model.UploadedFile;

import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lk.gov.health.dengue.Area;
import lk.gov.health.dengue.AreaSummery;
import lk.gov.health.dengue.AreaType;
import lk.gov.health.dengue.Coordinate;
import lk.gov.health.dengue.Institution;
import lk.gov.health.dengue.Sex;
import lk.gov.health.faces.CoordinateFacade;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.primefaces.model.map.DefaultMapModel;
import org.primefaces.model.map.LatLng;
import org.primefaces.model.map.MapModel;
import org.primefaces.model.map.Polygon;
import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

@Named("notificationController")
@SessionScoped
public class NotificationController implements Serializable {

    @EJB
    private lk.gov.health.faces.NotificationFacade ejbFacade;

    @Inject
    InstitutionController institutionController;
    @Inject
    AreaController areaController;
    @Inject
    CoordinateFacade coordinateFacade;

    private List<Notification> items = null;
    private Notification selected;
    private UploadedFile file;

    private Date fromDate;
    private Date toDate;
    private Area mohArea;
    private Area rdhsArea;
    private Area pdhsArea;

    private List<AreaSummery> areaSummerys;
    List<Notification> areaNotifications;

    private TimelineModel model;
    private MapModel polygonModel;

    public String toUploadExcelFile(){
        return "/nitification/upload_excel";
    }
    
    public String listMohAreaSummeries() {
        List<Area> gns = areaController.getGnAreasOfMoh(mohArea);
        areaSummerys = new ArrayList<AreaSummery>();
        areaNotifications = new ArrayList<Notification>();
        for (Area a : gns) {
            AreaSummery as = new AreaSummery();
            as.setArea(a);
            as.setCount(0);
            areaSummerys.add(as);
        }
        Map m = new HashMap();
        String j;
        j = "select n "
                + " from Notification n "
                + " where n.gnDivision.mohArea=:moh "
                + " and n.SendDate between :sdf and :sdt ";
        m.put("moh", mohArea);
        m.put("sdf", fromDate);
        m.put("sdf", toDate);
        areaNotifications = getFacade().findBySQL(j, m);
        for (AreaSummery as : areaSummerys) {
            for (Notification n : areaNotifications) {
                if(as.getArea().equals(n.getGnDivision())){
                    as.setCount(as.getCount()+ 1);
                }
            }
        }
        createTimeLineOfNotifications();
        markGnMapForSummeries();
        return "/notification/moh_summery";
    }

    public String toAnalyzeGnByMoh(){
        return "/notification/moh_summery";
    }
    
    public void createTimeLineOfNotifications() {
        model = new TimelineModel();
        for (Notification s : areaNotifications) {
            if (s != null && s.getGnDivision() != null && s.getSendDate() != null) {
                model.add(new TimelineEvent(s.getGnDivision().getName(), s.getSendDate()));
            }
        }
    }

    private void markGnMapForSummeries() {
        polygonModel = new DefaultMapModel();
        for (AreaSummery a : areaSummerys) {
            Polygon polygon = new Polygon();
            String j = "select c from Coordinate c where c.area=:a";
            Map m = new HashMap();
            m.put("a", a);
            List<Coordinate> cs = coordinateFacade.findBySQL(j, m);
            for (Coordinate c : cs) {
                LatLng coord = new LatLng(c.getLatitude(), c.getLongitude());
                polygon.getPaths().add(coord);
            }
            polygon.setStrokeColor("#FF9900");
            polygon.setFillColor("#D8000C");
            polygon.setStrokeOpacity(1);
            polygon.setFillOpacity(0.9);
            polygon.setData(a.getArea().getName());
            polygonModel.addOverlay(polygon);
        }
    }

    public UploadedFile getFile() {
        return file;
    }

    public void setFile(UploadedFile file) {
        this.file = file;
    }

    public String uploadNotificationExcelFile() {
        if (file == null || "".equals(file.getFileName())) {
            return "";
        }
        if (file == null) {
            JsfUtil.addErrorMessage("Please select an CSV File");
            return "";
        }

        InputStream in;
        JsfUtil.addSuccessMessage(file.getFileName() + " file uploaded.");
        try {
            JsfUtil.addSuccessMessage(file.getFileName());
            in = file.getInputstream();
            File f;
            f = new File(Calendar.getInstance().getTimeInMillis() + file.getFileName());
            FileOutputStream out = new FileOutputStream(f);
            int read = 0;
            byte[] bytes = new byte[1024];
            while ((read = in.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            in.close();
            out.flush();
            out.close();

            FileInputStream excelFile = new FileInputStream(new File(f.getAbsolutePath()));
            Workbook workbook = new XSSFWorkbook(excelFile);
            DataFormatter formatter = new DataFormatter();

            JsfUtil.addSuccessMessage("Excel File Opened");

            Sheet sheet1 = workbook.getSheetAt(0);

            int rowCount = 0;
            for (Row row : sheet1) {

                if (rowCount > 0) {
                    Notification n = new Notification();

                    int colNo = 0;
                    for (Cell cell : row) {
                        CellReference cellRef = new CellReference(row.getRowNum(), cell.getColumnIndex());
                        System.out.print(cellRef.formatAsString());
                        System.out.print(" - ");

                        String strVal = "";
                        Date dateVal = null;
                        int intVal = 0;
                        Double dblVal = 0.00;
                        Boolean boolVal = false;

                        switch (cell.getCellTypeEnum()) {
                            case STRING:
                                strVal = cell.getRichStringCellValue().getString();
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    dateVal = cell.getDateCellValue();
                                    strVal = dateVal + "";
                                } else {
                                    dblVal = cell.getNumericCellValue();
                                    intVal = dblVal.intValue();
                                    strVal = dblVal + "";
                                }
                                break;
                            case BOOLEAN:
                                boolVal = cell.getBooleanCellValue();
                                break;
                            case FORMULA:
                                System.out.println(cell.getCellFormula());
                                break;
                            case BLANK:
                                System.out.println();
                                break;
                            default:
                                System.out.println();
                        }

                        switch (colNo) {
                            case 0:
                                n.setSerialNo(strVal);
                                break;
                            case 1:
                                n.setSendDate(dateVal);
                                break;
                            case 2:
                                Institution hospital = institutionController.getInstitutionsByName(strVal);
                                if (hospital == null) {
                                    JsfUtil.addErrorMessage(strVal + " is not a recognised hospital");
                                    System.out.println(strVal + " is not a recognised hospital");
                                }
                                n.setHospital(hospital);
                                break;
                            case 3:
                                n.setAddmitedDate(dateVal);
                                break;
                            case 4:
                                strVal = formatter.formatCellValue(cell);
                                n.setBht(strVal);
                                break;
                            case 5:
                                n.setPatientsName(strVal);
                                break;
                            case 6:
                                if (intVal == 0) {
                                    strVal = strVal.replaceAll("\\D+", "");
                                    intVal = Integer.parseUnsignedInt(strVal);
                                }
                                n.setAge(intVal);
                                break;
                            case 7:
                                if (strVal.trim().toLowerCase().equals("male")) {
                                    n.setGender(Sex.Male);
                                } else {
                                    n.setGender(Sex.Female);
                                }
                                break;
                            case 9:
                                strVal = strVal.replaceAll("\\s+", "");
                                Area gnArea = areaController.getArea(strVal, AreaType.GN);
                                n.setGnDivision(gnArea);
                                if (gnArea == null) {
                                    JsfUtil.addErrorMessage(strVal + " is not a recognised GN Area");
                                    System.out.println(strVal + " is not a recognised GN Area");
                                }
                                break;
                            case 8:
                                if (n.getGnDivision() != null && n.getGnDivision().getParentArea() != null) {
                                    n.setPhiArea(n.getGnDivision().getParentArea().getParentArea());
                                }
                                break;
                            case 10:
                                n.setAddress(strVal);
                                break;
                            case 11:
                                strVal = formatter.formatCellValue(cell);
                                n.setTel(strVal);
                                break;
                            case 12:
                                n.setFooging(boolVal);
                                break;
                        }

                        colNo++;
                    }
                    getFacade().create(n);
                }

                rowCount++;
            }

            JsfUtil.addSuccessMessage("Succesfully added " + rowCount + " notifications.");

        } catch (IOException ex) {
            JsfUtil.addErrorMessage(ex.getMessage());
            return "";
        }

        return "";
    }

    public NotificationController() {
    }

    public Notification getSelected() {
        return selected;
    }

    public void setSelected(Notification selected) {
        this.selected = selected;
    }

    protected void setEmbeddableKeys() {
    }

    protected void initializeEmbeddableKey() {
    }

    private NotificationFacade getFacade() {
        return ejbFacade;
    }

    public Notification prepareCreate() {
        selected = new Notification();
        initializeEmbeddableKey();
        return selected;
    }

    public void create() {
        persist(PersistAction.CREATE, ResourceBundle.getBundle("/BundleDengue").getString("NotificationCreated"));
        if (!JsfUtil.isValidationFailed()) {
            items = null;    // Invalidate list of items to trigger re-query.
        }
    }

    public void update() {
        persist(PersistAction.UPDATE, ResourceBundle.getBundle("/BundleDengue").getString("NotificationUpdated"));
    }

    public void destroy() {
        persist(PersistAction.DELETE, ResourceBundle.getBundle("/BundleDengue").getString("NotificationDeleted"));
        if (!JsfUtil.isValidationFailed()) {
            selected = null; // Remove selection
            items = null;    // Invalidate list of items to trigger re-query.
        }
    }

    public List<Notification> getItems() {
        if (items == null) {
            items = getFacade().findAll();
        }
        return items;
    }

    private void persist(PersistAction persistAction, String successMessage) {
        if (selected != null) {
            setEmbeddableKeys();
            try {
                if (persistAction != PersistAction.DELETE) {
                    getFacade().edit(selected);
                } else {
                    getFacade().remove(selected);
                }
                JsfUtil.addSuccessMessage(successMessage);
            } catch (EJBException ex) {
                String msg = "";
                Throwable cause = ex.getCause();
                if (cause != null) {
                    msg = cause.getLocalizedMessage();
                }
                if (msg.length() > 0) {
                    JsfUtil.addErrorMessage(msg);
                } else {
                    JsfUtil.addErrorMessage(ex, ResourceBundle.getBundle("/BundleDengue").getString("PersistenceErrorOccured"));
                }
            } catch (Exception ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
                JsfUtil.addErrorMessage(ex, ResourceBundle.getBundle("/BundleDengue").getString("PersistenceErrorOccured"));
            }
        }
    }

    public Notification getNotification(java.lang.Long id) {
        return getFacade().find(id);
    }

    public List<Notification> getItemsAvailableSelectMany() {
        return getFacade().findAll();
    }

    public List<Notification> getItemsAvailableSelectOne() {
        return getFacade().findAll();
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
    }

    public Area getMohArea() {
        return mohArea;
    }

    public void setMohArea(Area mohArea) {
        this.mohArea = mohArea;
    }

    public Area getRdhsArea() {
        return rdhsArea;
    }

    public void setRdhsArea(Area rdhsArea) {
        this.rdhsArea = rdhsArea;
    }

    public Area getPdhsArea() {
        return pdhsArea;
    }

    public void setPdhsArea(Area pdhsArea) {
        this.pdhsArea = pdhsArea;
    }

    public List<AreaSummery> getAreaSummerys() {
        return areaSummerys;
    }

    public void setAreaSummerys(List<AreaSummery> areaSummerys) {
        this.areaSummerys = areaSummerys;
    }

    @FacesConverter(forClass = Notification.class)
    public static class NotificationControllerConverter implements Converter {

        @Override
        public Object getAsObject(FacesContext facesContext, UIComponent component, String value) {
            if (value == null || value.length() == 0) {
                return null;
            }
            NotificationController controller = (NotificationController) facesContext.getApplication().getELResolver().
                    getValue(facesContext.getELContext(), null, "notificationController");
            return controller.getNotification(getKey(value));
        }

        java.lang.Long getKey(String value) {
            java.lang.Long key;
            key = Long.valueOf(value);
            return key;
        }

        String getStringKey(java.lang.Long value) {
            StringBuilder sb = new StringBuilder();
            sb.append(value);
            return sb.toString();
        }

        @Override
        public String getAsString(FacesContext facesContext, UIComponent component, Object object) {
            if (object == null) {
                return null;
            }
            if (object instanceof Notification) {
                Notification o = (Notification) object;
                return getStringKey(o.getId());
            } else {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "object {0} is of type {1}; expected type: {2}", new Object[]{object, object.getClass().getName(), Notification.class.getName()});
                return null;
            }
        }

    }

}