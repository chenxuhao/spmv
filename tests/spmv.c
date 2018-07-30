//#include "spmv.h"
#include "timer.h"
#include "graph_io.h"

void SpmvSolver(int num_rows, int nnz, IndexType *Ap, IndexType *Aj, ValueType *Ax, ValueType *x, ValueType *y, int *degree) {
	Timer t;
	t.Start();
	for (int i = 0; i < num_rows; i++){
		const IndexType row_begin = Ap[i];
		const IndexType row_end   = Ap[i+1];
		ValueType sum = y[i];
		//#pragma omp simd reduction(+:sum)
		for (IndexType jj = row_begin; jj < row_end; jj++) {
			const IndexType j = Aj[jj];  //column index
			sum += x[j] * Ax[jj];
		}
		y[i] = sum; 
	}
	t.Stop();
	printf("\truntime = %f ms.\n", t.Millisecs());
	return;
}

int main(int argc, char *argv[]) {
	printf("Sparse Matrix-Vector Multiplication by Xuhao Chen\n");
	bool is_directed = true;
	bool symmetrize = false;
	if (argc < 2) {
		printf("Usage: %s <graph> [is_directed(0/1)]\n", argv[0]);
		exit(1);
	} else if (argc> 2) {
		is_directed = atoi(argv[2]);
		if(is_directed) printf("A is not a symmetric matrix\n");
		else printf("A is a symmetric matrix\n");
	}
	if(!is_directed) symmetrize = true;

	int m, n, nnz;
	IndexType *h_row_offsets = NULL, *h_column_indices = NULL;
	int *h_degree = NULL;
	ValueType *h_weight = NULL;
	read_graph(argc, argv, m, n, nnz, h_row_offsets, h_column_indices, h_degree, h_weight, symmetrize, false, true, false, true);
	int num_rows = m;
	int num_cols = m;
	ValueType *h_x = (ValueType *)malloc(m * sizeof(ValueType));
	ValueType *h_y = (ValueType *)malloc(m * sizeof(ValueType));
	ValueType *y_host = (ValueType *)malloc(m * sizeof(ValueType));
	srand(13);
	for(int i = 0; i < nnz; i++) h_weight[i] = 1.0 - 2.0 * (rand() / (RAND_MAX + 1.0)); // Ax[] (-1 ~ 1)
	for(int i = 0; i < num_cols; i++) h_x[i] = rand() / (RAND_MAX + 1.0);
	for(int i = 0; i < num_cols; i++) h_x[i] = 1.0;
	for(int i = 0; i < num_rows; i++) {
		h_y[i] = 0.0;//rand() / (RAND_MAX + 1.0);
		y_host[i] = h_y[i];
	}

	SpmvSolver(m, nnz, h_row_offsets, h_column_indices, h_weight, h_x, h_y, h_degree);
	//SpmvVerifier(m, nnz, h_row_offsets, h_column_indices, h_weight, h_x, y_host, h_y);
	free(h_row_offsets);
	free(h_column_indices);
	free(h_degree);
	free(h_weight);
	free(h_x);
	free(h_y);
	return 0;
}
