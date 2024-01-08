package study.querydsl;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.entity.Member;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);
        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    void testJPQL() {
        // Member1 찾기
        Member findMember = em.createQuery("select m from Member m where m.username= :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void testQuerydsl() {
//        QMember m = new QMember("m"); // 별칭 사용하기
//        QMember m = QMember.member;     // 제공하는 인스턴스 사용하기(static 변수이기 때문에 바로 사용해도 무방)
        // 같은 테이블을 조인해야 할 때 이 방법을 사용

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    void testSearch() {

        Member member1 = queryFactory
                // select * from member
                .selectFrom(member)
                // where username = 'member1' and age = 10
                .where(member.username.eq("member1").and(member.age.eq(10)))
                .fetchOne();

        assertThat(member1.getUsername()).isEqualTo("member1");
        assertThat(member1.getAge()).isEqualTo(10);
    }

    @Test
    void testSearchAndParam() {

        Member member1 = queryFactory
                // select * from member
                .selectFrom(member)
                // where username = 'member1' and age = 10
                .where(
                        // comma로 구분가능
                        member.username.eq("member1"),
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(member1.getUsername()).isEqualTo("member1");
        assertThat(member1.getAge()).isEqualTo(10);
    }

    @Test
    void resultFetchTest() {

        // 리스트 조회
        List<Member> memberList = queryFactory
                .selectFrom(member)
                .fetch();

        // 단 건 조회 값이 없다면 null 두 개 이상 값이 존재한다면 exception 발생
        Member memberFetch = queryFactory
                .selectFrom(member)
                .fetchOne();

        // 단 건 조회 limit를 사용한 것과 동일
        Member memberFetchFirst = queryFactory
                .selectFrom(member)
//                .limit(1).fetchOne();
                .fetchFirst();

        // paging 정보 제공
        // 성능이 중요한 페이지에서는 이 방법을 사용하면 안 됨
        QueryResults<Member> fetchResults = queryFactory
                .selectFrom(member)
                .fetchResults();

        fetchResults.getTotal();
        List<Member> results = fetchResults.getResults();

        long memberCount = queryFactory
                .selectFrom(member)
                .fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 나이 내림차순(desc)
     * 2. 이름 올림차순(asc)
     * 단, 2에서 이름이 없으면 마지막에 정렬(nulls last)
     */
    @Test
    void sort() {

        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> memberList = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = memberList.get(0);
        Member member6 = memberList.get(1);
        Member memberNull = memberList.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    void paging1() {

        List<Member> memberList = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(memberList.size()).isEqualTo(2);
    }

    @Test
    void aggregation() {

        // 값이 하나이기 때문에 fetch()가 아닌 fetchOne()으로 조회해도 된다.
        List<Tuple> tuples = queryFactory
                .select(
                        member.count(),
                        member.age.min(),
                        member.age.max(),
                        member.age.sum(),
                        member.age.avg()
                )
                .from(member)
                .fetch();

        Tuple tuple = tuples.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
    }

    /**
     * 팀 이름과 각 팀의 평균 연령 구하기
     */
    @Test
    void groupBy() {
        List<Tuple> results = queryFactory
                .select(team.name,
                        member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = results.get(0);
        Tuple teamB = results.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);

    }
}
