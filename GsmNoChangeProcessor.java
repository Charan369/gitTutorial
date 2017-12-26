package nl.yestelecom.middleware.processor;

import java.util.List;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import nl.yestelecom.middleware.callback.GsmNoChangeMWPostProcessor;
import nl.yestelecom.middleware.dao.zygo.ZygoDbDao;
import nl.yestelecom.middleware.endpoint.GsmNoChangeService;
import nl.yestelecom.middleware.model.ConnectionDTO;
import nl.yestelecom.middleware.model.WorkOrderDTO;
import nl.yestelecom.middleware.repo.MWChangeRepository;
import nl.yestelecom.middleware.repo.MWConnectionRepository;
import nl.yestelecom.middleware.util.LoginUtil;
import nl.yestelecom.middleware.util.MwErrorUtil;
import nl.yestelecom.phoenix.connection.model.ChangeRequest;
import nl.yestelecom.phoenix.middleware.model.MWErrorType.MWErrorTypeCode;
import nl.yestelecom.phoenix.scheduler.util.Job;
import nl.yestelecom.phoenix.scheduler.util.JobRunner;
import nl.yestelecom.phoenix.scheduler.util.ScheduledJobName;
import nl.yestelecom.phoenix.sim.model.GsmNumber;
import nl.yestelecom.phoenix.sim.model.GsmSim;
import nl.yestelecom.phoenix.sim.repository.GsmNumberRepository;

@Service
@Transactional
public class GsmNoChangeProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(GsmNoChangeProcessor.class);

    @Autowired
    private LoginUtil loginUtil;
    @Autowired
    private MWChangeRepository changeRepo;
    @Autowired
    private MWConnectionRepository gsmSimRepo;
    @Autowired
    private GsmNumberRepository gsmNumberRepo;
    @Autowired
    private ZygoDbDao zygoDbDao;
    @Autowired
    private GsmNoChangeService gsmNoChangeService;
    @Autowired
    private GsmNoChangeMWPostProcessor gsmNoChangePostProcessor;
    @Autowired
    private JobRunner jobRunner;
    @Autowired
    private MwErrorUtil errorUtil;

    public void process() {
        loginUtil.login();
        Job activation = () -> {
            List<ChangeRequest> gsmNoChanges = changeRepo.findGsmNoChanges();

            for (ChangeRequest gsmNoChange : gsmNoChanges) {
                try {
                    processGsmNoChange(gsmNoChange);
                } catch (Exception e) {
                    handleError(gsmNoChange, e);
                }
            }
        };
        jobRunner.run(activation, ScheduledJobName.MW_MOBILE_NUMBER_CHANGE);
    }

    private void processGsmNoChange(ChangeRequest gsmNoChange) throws Exception {
        GsmSim gsmSim = gsmSimRepo.findOne(gsmNoChange.getRefId());
        GsmNumber newGsmNumber = gsmNumberRepo.findByGsmNo(gsmNoChange.getValue());

        ConnectionDTO connection = zygoDbDao.findConnection(gsmSim.getServiceCode());

        LOG.info("Old gsm no in C2Y - {}", gsmSim.getGsmNumber().getGsmNo());
        LOG.info("Old gsm no in Zygo - {}", connection == null ? null : connection.getGsmNumber());
        LOG.info("New gsm no - {}", newGsmNumber.getGsmNo());

        if (connection == null) {
            String errorMessage = "Connection is not present in zygo. GsmNo change could not be performed. GsmNoChangeId - " + gsmNoChange.getId();
            // LOG.error(errorMessage);
            throw new Exception(errorMessage);
        } else if (connection.getGsmNumber() == null) {
            String errorMessage = "Connection does not have an existing gsm no. GsmNo change could not be performed. GsmNoChangeId - " + gsmNoChange.getId();
            // LOG.error(errorMessage);
            throw new Exception(errorMessage);
        } else if ("C".equalsIgnoreCase(connection.getStatus()) || "S".equalsIgnoreCase(connection.getStatus())) {
            LOG.info("The Connection is already disconnected. GsmNo change could not be performed. GsmNoChangeId - {}", gsmNoChange.getId());
            changeRepo.delete(gsmNoChange);
            return;
        } else if (connection.getGsmNumber().equals(getGSMNumber(newGsmNumber, gsmSim))) {
            LOG.info("The Connection already has the correct GsmNo. GsmNoChangeId - {}" + gsmNoChange.getId());
            gsmNoChangePostProcessor.gsmNoChanged(gsmNoChange.getId());
        } else {
            // Legit case for gsm no change
            gsmNoChangeService.changeGsmNo(getGSMNumber(newGsmNumber, gsmSim), gsmSim.getServiceCode());
            gsmNoChangePostProcessor.gsmNoChanged(gsmNoChange.getId());
        }

    }

    private String getGSMNumber(GsmNumber newGsmNumber, GsmSim gsmSim) {
        String gsmNo = "";
        if (newGsmNumber != null && gsmSim != null && gsmSim.getDataSubscriptionPlan() != null && gsmSim.getDataSubscriptionPlan().getSubscriptionGroup() != null
                && "M2M".equals(gsmSim.getDataSubscriptionPlan().getSubscriptionGroup().getCode())) {
            gsmNo = "0" + newGsmNumber.getGsmNo();
        } else if (newGsmNumber != null) {
            gsmNo = "06" + newGsmNumber.getGsmNo();
        }
        return gsmNo;
    }

    private void handleError(ChangeRequest gsmNumberChange, Exception e) {
        LOG.error("Error in processing Sim change request. ChangeRequestId - {}. Error message - {}", gsmNumberChange.getId(), e.getMessage(), e);

        GsmSim gsmSim = gsmSimRepo.findOne(gsmNumberChange.getRefId());
        //@formatter:off
        WorkOrderDTO workOrderDTO = WorkOrderDTO.builder()
                .referenceId(gsmNumberChange.getId())
                .errorType(MWErrorTypeCode.GSM_NUMBER_CHANGE)
                .exception(e)
                .gsmSim(gsmSim)
                .build();
        //@formatter:on
        errorUtil.createMwError(workOrderDTO);
    }

}
