package cait.collector.web.model.response;

import lombok.*;

import java.util.Collection;
import java.util.Collections;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagingData<T> {
    private long currentPage;
    private long pageSize;
    private long totalPage;

    private long totalResult;

    private Collection<T> data;

    public PagingData(PagingData<?> origin) {
        this(origin, Collections.emptyList());
    }

    public PagingData(PagingData<?> origin, Collection<T> data) {
        this.currentPage = origin.currentPage;
        this.pageSize = origin.pageSize;
        this.totalPage = origin.totalPage;
        this.totalResult = origin.totalResult;

        this.data = data;
    }

    public PagingData(long currentPage, long pageSize) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
    }

    public PagingData<T> copy() {
        PagingData<T> pagingData = new PagingData<>();

        pagingData.setCurrentPage(this.getCurrentPage());
        pagingData.setPageSize(this.getPageSize());
        pagingData.setTotalPage(this.getTotalPage());
        pagingData.setTotalResult(this.getTotalResult());
        pagingData.setData(this.getData());

        return pagingData;
    }
    
    public static <K> PagingData<K> copyOnlyPagingValues(PagingData<?> source) {
        PagingData<K> pagingData = new PagingData<>();

        pagingData.setCurrentPage(source.getCurrentPage());
        pagingData.setPageSize(source.getPageSize());
        pagingData.setTotalPage(source.getTotalPage());
        pagingData.setTotalResult(source.getTotalResult());

        return pagingData;
    }
}
