import {Component, OnInit, ViewChild} from '@angular/core';
import {ApiService} from "../service/api.service";
import {Operation, OperationSummaryList} from "../model/operation";
import {EMPTY, empty, interval, of} from "rxjs";
import {catchError, startWith, switchMap} from "rxjs/operators";
import {MineDialogComponent} from "./mine-dialog/mine-dialog.component";
import {LogsDialogComponent} from "./logs-dialog/logs-dialog.component";
import {MatDialog, MatDialogConfig} from "@angular/material/dialog";
import {animate, state, style, transition, trigger} from "@angular/animations";
import {ConfiguredAltchain} from "../model/configured-altchain";
import {MatPaginator, PageEvent} from "@angular/material/paginator";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({height: '0px', minHeight: '0'})),
      state('expanded', style({height: '*'})),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ],
})
export class AppComponent implements OnInit {

  configuredAltchains: ConfiguredAltchain[] = []

  vbkAddress: string
  vbkBalance: string

  operations: Operation[] = []
  selectedOperationId: string
  columnsToDisplay = ['operationId', 'chain', 'state', 'task']

  statusFilter = 'active'
  pageLimit = 50
  pageOffset = 0

  operationWorkflows = {}

  trackByOperationId = (index, operation) => operation.operationId;

  constructor(
    private apiService: ApiService,
    private dialog: MatDialog
  ) {
  }

  ngOnInit() {
    // Get the configured altchains
    this.apiService.getConfiguredAltchains().subscribe(configuredAltchains => {
      this.configuredAltchains = configuredAltchains.altchains
    })
    // Check the miner data API every 61 seconds
    interval(61_000).pipe(
      startWith(0),
      switchMap(() => this.apiService.getMinerInfo())
    ).subscribe(response => {
      this.vbkAddress = response.vbkAddress
      this.vbkBalance = (response.vbkBalance / 100_000_000) + ' VBK'
    })

    // Check the operation list API every 2 seconds
    interval(2_000).pipe(
      startWith(0),
      switchMap(() => this.apiService.getOperations(this.statusFilter, this.pageLimit, this.pageOffset))
    ).subscribe(response => {
      this.operations = response.operations
    })
    // Check the operation details API every 5 seconds
    interval(5_000).pipe(
      startWith(0),
      switchMap(() => this.selectedOperationId ? this.apiService.getOperationWorkflow(this.selectedOperationId) : EMPTY)
    ).subscribe(workflow => {
      this.operationWorkflows[workflow.operationId] = workflow
    })
  }

  selectOperation(operation: Operation) {
    if (operation.operationId == this.selectedOperationId) {
      this.selectedOperationId = null
      return
    }
    this.apiService.getOperationWorkflow(operation.operationId).subscribe(workflow => {
      this.operationWorkflows[operation.operationId] = workflow
      this.selectedOperationId = operation.operationId;
    })
  }

  openMineDialog() {
    const dialogConfig = new MatDialogConfig()
    dialogConfig.data = this.configuredAltchains
    this.dialog.open(MineDialogComponent, dialogConfig)
  }

  openLogsDialog(level: string) {
    this.apiService.getOperationLogs(this.selectedOperationId, level).subscribe(logs => {
      const dialogConfig = new MatDialogConfig()
      dialogConfig.data = logs
      this.dialog.open(LogsDialogComponent, dialogConfig)
    })
  }

  changeStatusFilter(filter: string) {
    if (!filter) {
      return
    }
    this.statusFilter = filter
    this.refreshOperationList();
  }

  changePage(event: PageEvent) {
    this.pageLimit = event.pageSize
    this.pageOffset = event.pageIndex * event.pageSize
    this.refreshOperationList();
  }

  private refreshOperationList() {
    this.apiService.getOperations(this.statusFilter, this.pageLimit, this.pageOffset).subscribe(response => {
      this.operations = response.operations
    })
  }
}


