package com.hirehub.applications;


import com.hirehub.model.*;
import com.hirehub.service.*;
import com.hirehub.dao.*;
import com.hirehub.database.*;
import com.hirehub.util.*;
import com.hirehub.controller.*;
import com.hirehub.dao.ApplicationDAO;
import com.hirehub.dao.JobDAO;
import com.hirehub.model.Application;
import com.hirehub.model.Job;
import com.hirehub.service.ApplicationService;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

/**
 * RecruiterApplicantManagementTest — automated unit & integration verifier
 * for Recruiter Applicant Management.
 */
public class RecruiterApplicantManagementTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  HireHub Recruiter Applicant Management Verifier");
        System.out.println("=================================================");

        testModelEnum();
        testDAOContract();
        testServiceAuthorizationLogic();

        System.out.println("\n-------------------------------------------------");
        System.out.println("Results: " + passed + " passed, " + failed + " failed.");
        System.out.println("=================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testModelEnum() {
        assertTest("Application.Status enum has 4 states",
                Application.Status.values().length == 4);
        assertTest("Status.fromString SHORTLISTED",
                Application.Status.fromString("SHORTLISTED") == Application.Status.SHORTLISTED);
        assertTest("Status.fromString PENDING",
                Application.Status.fromString("PENDING") == Application.Status.PENDING);
        assertTest("Status.fromString REJECTED",
                Application.Status.fromString("REJECTED") == Application.Status.REJECTED);
        assertTest("Status.fromString SELECTED",
                Application.Status.fromString("SELECTED") == Application.Status.SELECTED);
    }

    private static void testDAOContract() {
        try {
            Class<?> daoClass = ApplicationDAO.class;
            Method m1 = daoClass.getMethod("countByJob", int.class);
            assertTest("ApplicationDAO has countByJob(int)", m1 != null);

            Method m2 = daoClass.getMethod("countTotalByRecruiter", int.class);
            assertTest("ApplicationDAO has countTotalByRecruiter(int)", m2 != null);

            Method m3 = daoClass.getMethod("countShortlistedByRecruiter", int.class);
            assertTest("ApplicationDAO has countShortlistedByRecruiter(int)", m3 != null);

            Method m4 = daoClass.getMethod("findByJobId", int.class);
            assertTest("ApplicationDAO has findByJobId(int)", m4 != null);

            Method m5 = daoClass.getMethod("updateStatus", int.class, Application.Status.class);
            assertTest("ApplicationDAO has updateStatus(int, Status)", m5 != null);
        } catch (Exception e) {
            assertTest("ApplicationDAO reflection checks failed: " + e.getMessage(), false);
        }
    }

    private static void testServiceAuthorizationLogic() {
        ApplicationService service = new ApplicationService();

        // 1. Unauthorised recruiter check on non-existent or other recruiter's job
        List<Application> apps = service.getApplicationsByJob(-999, -999);
        assertTest("getApplicationsByJob returns empty list for invalid/unowned job", apps != null && apps.isEmpty());

        // 2. Unauthorised status update
        ApplicationService.ServiceResult res = service.updateApplicationStatus(-999, -999, Application.Status.SHORTLISTED);
        assertTest("updateApplicationStatus rejects invalid/unowned application", !res.isSuccess());

        // 3. Count methods return safe defaults without throwing uncaught exceptions
        int jobApps = service.countApplicationsForJob(-999);
        assertTest("countApplicationsForJob returns >= 0 safely", jobApps >= 0);

        int recApps = service.countTotalApplicationsForRecruiter(-999);
        assertTest("countTotalApplicationsForRecruiter returns >= 0 safely", recApps >= 0);

        int shortlisted = service.countShortlistedForRecruiter(-999);
        assertTest("countShortlistedForRecruiter returns >= 0 safely", shortlisted >= 0);
    }

    private static void assertTest(String name, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.err.println("  [FAIL] " + name);
            failed++;
        }
    }
}

