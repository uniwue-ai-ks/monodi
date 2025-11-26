import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private userS: BehaviorSubject<User | null> = new BehaviorSubject<User | null>(null);
  public  user:  Observable<User | null> = this.userS;

  constructor(private router: Router) {
    const lsUser = localStorage.getItem("user");
    if (lsUser) {
      this.userS.next(JSON.parse(lsUser));
    }
  }

  public setUser(u: User): void {
    localStorage.setItem("user", JSON.stringify(u));
    this.userS.next(u);
  }

  public logout(): void {
    this.router.navigate(['/login']);
    this.userS.next(null);
  }
}

export interface User {
  roles: string[];
  token: string;
  user:  string;
}
