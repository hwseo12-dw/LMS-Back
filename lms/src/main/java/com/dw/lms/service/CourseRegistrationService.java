package com.dw.lms.service;

import com.dw.lms.dto.LectureStatusCountDto;
import com.dw.lms.model.Course_registration;
import com.dw.lms.model.Lecture;
import com.dw.lms.model.User;
import com.dw.lms.repository.CourseRegistrationRepository;
import com.dw.lms.repository.LectureRepository;
import com.dw.lms.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CourseRegistrationService {
    @Autowired
    CourseRegistrationRepository courseRegistrationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    LectureRepository lectureRepository;

    public List<Course_registration> getAllRegistration() {
        return courseRegistrationRepository.findAll();
    }

    @PersistenceContext
    private EntityManager entityManager;

    // 수강목차의 마지막 횟차 가져오는 SQL
    public List<Object> executeNativeQueryFLCS(String lectureId) {
        String sqlQuery = "select max(learning_contents_seq) from learning_contents where lecture_id = :lectureId";
        Query query = entityManager.createNativeQuery(sqlQuery);
        query.setParameter("lectureId", lectureId);
        return query.getResultList(); // Returns a list of Objects
    }

    // 수강목차의 마지막 횟차 가져오는 SQL 실행 후 값을 가져오는 부분
    public Long getFinalLectureContentsSeqJPQL(String lectureId) {
        List<Object> results = executeNativeQueryFLCS(lectureId);

        Long column1Value = 0L; // 초기값을 Long 타입으로 설정

        System.out.println("getFinalLectureContentsSeqJPQL Start!!!");

        if (!results.isEmpty()) {
            Object result = results.get(0);
            if (result != null) {
                column1Value = ((Number) result).longValue(); // Object를 Number로 캐스팅하여 Long 값으로 변환
                System.out.println("column1Value: " + column1Value);
            }
        }

        System.out.println("getFinalLectureContentsSeqJPQL End!!!");

        return column1Value;
    }

    // 수강신청을 하는 부분
    public String saveCourseRegistration(Course_registration course_registration) {
        try {
            // 입력된 리뷰의 course_registration에서 user와 lecture를 가져와 객체를 생성

            User user = userRepository.findById(course_registration.getUser().getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));
            Lecture lecture = lectureRepository.findById(course_registration.getLecture().getLectureId())
                    .orElseThrow(() -> new EntityNotFoundException("Lecture not found"));

            course_registration.setUser(user);
            course_registration.setLecture(lecture);

            System.out.println("getLectureId:" + course_registration.getLecture().getLectureId());

            Long finalSeq = getFinalLectureContentsSeqJPQL(course_registration.getLecture().getLectureId());
            course_registration.setFinalLectureContentsSeq(finalSeq);

            System.out.println("getFinalLectureContentsSeqJPQL:" + finalSeq);

            Long contentSeq = Long.valueOf(0);
            course_registration.setProgressLectureContentsSeq(contentSeq);

            course_registration.setLectureStatus("I");
            course_registration.setLectureCompletedCheck("N");

            // 현재 날짜와 시간을 한 번만 가져옴
            LocalDateTime now = LocalDateTime.now();
            course_registration.setCourseRegistrationDate(now.toLocalDate()); // 현재일자
            course_registration.setSysDate(now); // 현재일시
            course_registration.setUpdDate(now); // 현재일시

            // 리뷰를 저장하고 저장된 리뷰의 userId 반환
            Course_registration savedReview = courseRegistrationRepository.save(course_registration);
            return savedReview.getUser().getUserId(); // 없으면 Insert, 있으면 Update
        } catch (Exception e) {
            // 예외 발생 시 로그를 남기고 null 반환 (혹은 적절한 예외 처리)
            // 예: log.error("Error saving review", e);
            //throw new ResourceNotFoundException("User", "ID", learning_review.getCourse_registration().getUser().getUserId());
            System.out.println("Error saving CourseRegistration: " + e);
            return "saveCourseRegistration Error!";
        }
    }

    public List<Object[]> executeNativeQueryDto(String userId) {
        String sqlQuery = "select 'A'        as lecture_status_id " +
                          "     , '수강신청' as lecture_status_name " +
                          "     , count(*)   as lecture_status_count " +
                          "     , 0          as sort_seq " +
                          "  from course_registration a " +
                          " where a.user_id = :userId " +
                          " union all " +
                          "select a.lecture_status as lecture_status_id " +
                          "     , b.code_name      as lecture_status_name " +
                          "     , count(*)         as category_count " +
                          "     , b.sort_seq       as sort_seq " +
                          "  from code_class_detail   b " +
                          "     , course_registration a " +
                          " where b.code_class = 'LECTURE_STATUS' " +
                          "   and a.lecture_status = b.code " +
                          "   and a.user_id = :userId " +
                          " group by " +
                          "       a.lecture_status " +
                          "     , b.code_name " +
                          "     , b.sort_seq " +
                          " union all " +
                          "select a.code      as lecture_status_id " +
                          "     , a.code_name as lecture_status_name " +
                          "     , 0           as category_count " +
                          "     , a.sort_seq  as sort_seq " +
                          "  from code_class_detail a " +
                          " where a.code_class = 'LECTURE_STATUS' " +
                          "   and not exists ( select * " +
                          "                      from course_registration b " +
                          "                     where b.user_id = :userId " +
                          "                       and b.lecture_status = a.code ) " +
                          " order by 4 ";
        Query query = entityManager.createNativeQuery(sqlQuery);
        query.setParameter("userId", userId);

        return query.getResultList(); // Returns a list of Object arrays
    }

    // Native Query 사용2 => Dto 에 담는 예제
    public List<LectureStatusCountDto> getLectureStatusCountJPQL(String userId) {

        List<LectureStatusCountDto> targetDto = new ArrayList<>();
        List<Object[]> results = executeNativeQueryDto(userId);

// [LectureCategoryCountDto]
//    private String LectureStatusId;
//    private String LectureStatusName;
//    private Long LectureStatusCount;

        // Process the results
        for (Object[] row : results) {
            // Access each column in the row
            System.out.println("StatusId:    "+row[0].toString());
            System.out.println("StatusName:  "+row[1].toString());
            System.out.println("StatusCount: "+row[2].toString());
            System.out.println("SortSeq:     "+row[3].toString());

            String  column1Value = row[0].toString(); // Assuming column1 is of type String
            String  column2Value = row[1].toString(); // Assuming column2 is of type String
            Long    column3Value = Long.valueOf(row[2].toString()); // Assuming column3 is of type int
            Long    column4Value = Long.valueOf(row[3].toString()); // Assuming column3 is of type int

            System.out.println("column1Value: "+ column1Value);
            System.out.println("column2Value: "+ column2Value);
            System.out.println("column3Value: "+ column3Value);
            System.out.println("column4Value: "+ column4Value);

            LectureStatusCountDto lectureStatusCountDto = new LectureStatusCountDto(column1Value, column2Value, column3Value, column4Value);
            targetDto.add(lectureStatusCountDto);
            // Do something with the values...
        }

        return targetDto;
    }

}
