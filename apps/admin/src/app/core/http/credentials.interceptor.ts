import { HttpInterceptorFn } from '@angular/common/http';

export const credentialsInterceptor: HttpInterceptorFn = (request, next) => {
  const apiRequest = request.clone({ withCredentials: true });
  return next(apiRequest);
};
